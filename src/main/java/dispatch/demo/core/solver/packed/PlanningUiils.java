package dispatch.demo.core.solver.packed;

import dispatch.demo.core.context.DispatchContext;
import dispatch.demo.core.route.Planner;
import dispatch.demo.dto.Courier;
import dispatch.demo.dto.Location;
import dispatch.demo.dto.Order;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.TriConsumer;


public class PlanningUiils {
    public static List<List<RichAction>> planTwo(DispatchContext context, Courier courier, List<Order> orders, Planner planner,final double threshold){
//        double threshold = 1e-5;
        List<List<RichAction>> r = new ArrayList<>();
        final int maxUsed = 20 ;
        int MaxRangeUsed[] = new int[orders.size()];
        int totalUsed[] = new int[orders.size()];
        for(int i=0;i < orders.size();i++){
            final Order o1 = orders.get(i);
            if(MaxRangeUsed[i] > maxUsed){
                continue;
            }
            for(int j=i+1;j < orders.size();j++){
                final Order o2 = orders.get(j);
                double d1 = o1.getSrcLoc().distanceTo(o2.getSrcLoc() );
                double d2 = o1.getDstLoc().distanceTo(o2.getDstLoc());
                if(d1> threshold
                        &&  d2> threshold
                ){
                    continue;
                }
                if(d1> 1e-5 && d2 > 1e-5){
                    if(MaxRangeUsed[i] > maxUsed || MaxRangeUsed[j] > maxUsed){
                        continue;
                    }
                    MaxRangeUsed[i]++;
                    MaxRangeUsed[j]++;
                }
                else{
                    if(totalUsed[i] > maxUsed*3 || totalUsed[j] > maxUsed*3  ){
                        continue;
                    }
                    totalUsed[i]++;
                    totalUsed[j]++;
                }

                List<List<RichAction>> allPlanns = new ArrayList<>();
                if(o1.getSrcLoc().distanceTo(o2.getSrcLoc() )<= threshold){
                    List<RichAction> p1 = Arrays.asList(
                            new RichAction(null,o1,1),new RichAction(null,o2,1),
                            new RichAction(null,o1,3),new RichAction(null,o2,3)
                    );
                    List<RichAction> p2 = Arrays.asList(
                            new RichAction(null,o1,1),new RichAction(null,o2,1),
                            new RichAction(null,o2,3),new RichAction(null,o1,3)
                    );
                    allPlanns.add(p1);
                    allPlanns.add(p2);
                }
                if(o1.getDstLoc().distanceTo(o2.getDstLoc()) <= threshold){
                    List<RichAction> p3 = Arrays.asList(
                            new RichAction(null,o2,1),new RichAction(null,o1,1),
                            new RichAction(null,o1,3),new RichAction(null,o2,3)
                    );
                    List<RichAction> p4 = Arrays.asList(
                            new RichAction(null,o2,1),new RichAction(null,o1,1),
                            new RichAction(null,o2,3),new RichAction(null,o1,3)
                    );
                    allPlanns.add(p3);
                    allPlanns.add(p4);
                }
                if(allPlanns.isEmpty()){
                    continue;
                }
                double minCost = Double.MAX_VALUE;
                List<RichAction> minPlan = null;

                for(List<RichAction> actionList : allPlanns){
                    List<RichAction> t = planner.plan(courier,actionList,context);
                    double c = uitls.calculateCost(context,t);
                    if(c < minCost){
                        minCost = c;
                        minPlan = t;
                    }
                }
                minPlan.get(minPlan.size()-1).setCost(minCost);
                r.add(minPlan);
            }
        }
        return r;
    }




    public static List<List<Order>> groupOrder(List<Order> orders,Function<Order,Location> keySelector,double threshold){
            Map<List<Long>,Set<Order>> msrc  = new HashMap<>();
            for ( Order  o : orders){
                long srclng = Math.round(keySelector.apply(o).getLongitude()/threshold);
                long srclat = Math.round(keySelector.apply(o).getLatitude()/threshold);
                List<Long> key = new ArrayList<>(2);
                key.add(srclng);
                key.add(srclat);
                if(!msrc.containsKey(key)){
                    msrc.put(key,new HashSet<>());
                }
                msrc.get(key).add(o);


                srclng = (long) Math.floor(keySelector.apply(o).getLongitude()/threshold);
                srclat = (long) Math.round(keySelector.apply(o).getLatitude()/threshold);
                key.clear();key.add(srclng);key.add(srclat);
                if(!msrc.containsKey(key)){
                    msrc.put(key,new HashSet<>());
                }
                msrc.get(key).add(o);

                srclng = (long) Math.round(keySelector.apply(o).getLongitude()/threshold);
                srclat = (long) Math.floor(keySelector.apply(o).getLatitude()/threshold);
                key.clear();key.add(srclng);key.add(srclat);
                if(!msrc.containsKey(key)){
                    msrc.put(key,new HashSet<>());
                }
                msrc.get(key).add(o);


                srclng = (long) Math.floor(keySelector.apply(o).getLongitude()/threshold);
                srclat = (long) Math.floor(keySelector.apply(o).getLatitude()/threshold);
                key.clear();key.add(srclng);key.add(srclat);
                if(!msrc.containsKey(key)){
                    msrc.put(key,new HashSet<>());
                }
                msrc.get(key).add(o);
            }
            List<List<Order>> rsrc = msrc.values().stream().map(ArrayList::new).collect(Collectors.toList());
            return rsrc;
    }

    /**
     *
     * @param orders
     * @param threshold
     * @return (same start,groupid,group)
     */
    @SneakyThrows
    public static List<List<List<Order>>> groupOrderAll(List<Order> orders, double threshold){
        List<List<List<Order>>> r = new ArrayList<>();
        Future<List<List<Order>>> f1 = PackedSolver.forkJoinPool.submit(
                ()-> {
                    List<List<Order>> r1 =   groupOrder(orders,Order::getSrcLoc,threshold);
                    r1.stream().parallel().flatMap(
                            (os)->{
                                List<List<Order>> t = new ArrayList<>();
                                if(os.size() < 20){
                                    t.add(os);
                                }
                                else {
                                    t.addAll(groupOrder(orders,Order::getDstLoc,threshold));
                                }
                                return t.stream();
                            }
                    ).collect(Collectors.toList());

                    return r1;
                });
        Future<List<List<Order>>> f2 = PackedSolver.forkJoinPool.submit(
                ()-> groupOrder(orders,Order::getDstLoc,threshold));

        r.add(f1.get());
        r.add(f2.get());
        return r;
    }

    public static List<List<List<RichAction>>> processGroupedOrder(List<List<List<Order>>> orders,final int maxUsed){
        Queue<List<List<RichAction>>> r2 = new ConcurrentLinkedQueue<>();
        for(int oi=0;oi < 2;oi++){
            orders.get(oi).stream().parallel().forEach(
                    (groupOrders)->{
                        int[] used = new int[groupOrders.size()];;
                        int[] usedSmall = new int[groupOrders.size()];
                        groupOrders.sort((a,b)-> (int) (a.getPromiseDeliverTime()-b.getPromiseDeliverTime()));

                        for(int i=0;i < groupOrders.size();i++){
                            final Order o1 = groupOrders.get(i);
                            if(used[i] > maxUsed && usedSmall[i] > maxUsed){
                                continue;
                            }
                            for(int j=i+1;j < groupOrders.size();j++){
                                final Order o2 = groupOrders.get(j);
                                if(o1.getSrcLoc().distanceTo(o2.getSrcLoc()) < 1e-16 || o1.getDstLoc().distanceTo(o2.getDstLoc()) < 1e-16 ){
                                    if(usedSmall[i] > maxUsed || usedSmall[j] > maxUsed){
                                        continue;
                                    }
                                    usedSmall[i]++;
                                    usedSmall[j]++;
                                }
                                else{
                                    if(used[i] > maxUsed || used[j] > maxUsed){
                                        continue;
                                    }
                                    used[i]++;
                                    used[j]++;
                                }

                                double d1 = o1.getSrcLoc().distanceTo(o2.getSrcLoc() );
                                double d2 = o1.getDstLoc().distanceTo(o2.getDstLoc());


                                List<List<RichAction>> allPlanns = new ArrayList<>();
                                List<RichAction> p1 = Arrays.asList(
                                        new RichAction(null,o1,1),new RichAction(null,o2,1),
                                        new RichAction(null,o1,3),new RichAction(null,o2,3)
                                );
                                List<RichAction> p2 = Arrays.asList(
                                        new RichAction(null,o1,1),new RichAction(null,o2,1),
                                        new RichAction(null,o2,3),new RichAction(null,o1,3)
                                );
                                allPlanns.add(p1);
                                allPlanns.add(p2);

                                List<RichAction> p3 = Arrays.asList(
                                        new RichAction(null,o2,1),new RichAction(null,o1,1),
                                        new RichAction(null,o1,3),new RichAction(null,o2,3)
                                );
                                List<RichAction> p4 = Arrays.asList(
                                        new RichAction(null,o2,1),new RichAction(null,o1,1),
                                        new RichAction(null,o2,3),new RichAction(null,o1,3)
                                );
                                allPlanns.add(p3);
                                allPlanns.add(p4);

                                r2.add(allPlanns);
                            }
                        }
                    }
            );
        }

        List<List<List<RichAction>>> r = new ArrayList<>(r2);
        return r;
    }

    /**
     * 针对起点相近的订单进行处理
     * @param orders
     * @param maxUsed
     * @return
     */
    public static List<List<List<RichAction>>> processExtraGroupedOrder(List<List<Order>> orders,final int maxUsed,double threshold){
//        orders = groupOrder(orders,Order::getDstLoc,threshold);
        Queue<List<Order>> groups = new ConcurrentLinkedQueue<>();
        orders.stream().parallel().
                forEach(os->{
                    List<List<Order>> temp = groupOrder(os,Order::getDstLoc,threshold);
                    groups.addAll(temp);
                });
        Queue<List<List<RichAction>>> r2 = new ConcurrentLinkedQueue<>();

        groups.stream().filter(x->x.size() >= 2).parallel()
                .forEach((g)->{
                    for(int end = 3;end < maxUsed && end< g.size();end++){
                        List<RichAction> temp = new ArrayList<>();
                        for(int s =0;s < end;s++){
                            temp.add(new RichAction(null, g.get(s),1));
                        }
                        for(int s =0;s < end;s++){
                            temp.add(new RichAction(null, g.get(s),3));
                        }
                        List<List<RichAction>> t= new ArrayList<>();
                        t.add(temp);
                        r2.add(t);
                    }
                });

        List<List<List<RichAction>>> r = new ArrayList<>();
        r.addAll(r2);
        return r;
    }


    static class DfsInterface<T>{
        public T func;
    };

    public static List<List<RichAction>> generateExtra(final List<List<List<Order>>> orders,final long filterTime,
                                                       final int minPlanning,final int maxPlanning){
        Function<Order, Location> keyselector = Order::getSrcLoc;
        assert orders.size() ==2;
        final int solveSize = 3;

        ConcurrentLinkedQueue<List<Order>> r = new ConcurrentLinkedQueue<>();
        for(int temp0 = 0;temp0< 2;temp0++){
            orders.get(temp0).stream().parallel().filter(os->os.size()>2).forEach(
                    (os_)->{
                        //filter out new node
                        final List<Order> os = os_.stream().filter(o->o.getEstimatedPrepareCompletedTime() > filterTime).collect(Collectors.toList());
                        //buildGraph
                        List<List<Pair<Integer,Double>>> edges = new ArrayList<>(os.size());
                        for(int i=0;i < os.size();i++){
                            edges.add(new ArrayList<>());
                        }
                        for(int i=0;i < os.size();i++){
                            for(int j=i+1;j < os.size();j++){
                                double distance = keyselector.apply(os.get(i)).distanceTo(keyselector.apply(os.get(j)));
                                edges.get(i).add(new ImmutablePair<>(j,distance));
                                edges.get(j).add(new ImmutablePair<>(i,distance));
                            }
                        }
                        //greedy Search
                        edges = edges.stream().map(x->{
                            x.sort((a,b)->{
                                double temp = a.getValue()-b.getValue();
                                return temp == 0?0:temp<0?1:-1;
                            });
                            return x;
                        }).map(x->{
                            if(x.size() > solveSize){
                                x = x.subList(0,solveSize);
                            }
                            return x;
                        }).collect(Collectors.toList());

                        DfsInterface<TriConsumer<Integer,List<Order>,Set<Order>>> dfsFunc= new DfsInterface<>();
                        List<List<Pair<Integer, Double>>> finalEdges = edges;
                        TriConsumer<Integer,List<Order>,Set<Order>> dfs = (cur, buffer, used)->{
                            if(buffer.size() >maxPlanning){
                                return;
                            }
                            if(buffer.size() >= minPlanning){
                                r.add(new ArrayList<>(buffer));
                            }
                            for(int i = 0; i< finalEdges.get(cur).size(); i++){
                                Order o = os.get(finalEdges.get(cur).get(i).getKey());
                                if(used.contains(o)){
                                    continue;
                                }
                                used.add(o);
                                buffer.add(o);
                                dfsFunc.func.accept(i,buffer,used);
                                used.remove(o);
                                buffer.remove(buffer.size()-1);
                            }
                        };
                        dfsFunc.func = dfs;


                        IntStream.range(0,edges.size()).parallel().forEach(
                            (istart)->{
                                Set<Order> used = new HashSet<>();
                                used.add(os.get(istart));
                                dfsFunc.func.accept(istart,new ArrayList<>(),used);
                            }
                        );
                    }

            );
        };
        List<List<RichAction>> result = r.stream().parallel().map(
                (planOrders)->{
                    final List<RichAction> temp = new ArrayList<>();
                    planOrders.stream().forEach((o)->{
                        temp.add(new RichAction(null,o,1));
                    });
                    planOrders.stream().forEach((o)->{
                        temp.add(new RichAction(null,o,3));
                    });
                    return temp;
                }
        ).collect(Collectors.toList());
        return result;
    }



    public static List<List<RichAction>> computeCost(DispatchContext context,Courier courier,Planner planner,List<List<List<RichAction>>> allPlans){
        List<List<RichAction>> r = new ArrayList<>();
        allPlans.stream().forEach(
                (plans)->{
                    if(plans.isEmpty()){
                        return;
                    }
                    double minCost = Double.MAX_VALUE;
                    List<RichAction> minPlan = null;

                    for(List<RichAction> actionList : plans){
                        List<RichAction> t = planner.plan(courier,actionList,context);
                        double c = uitls.calculateCost(context,t);
                        if(c < minCost){
                            minCost = c;
                            minPlan = t;
                        }
                    }
                    if(minPlan == null) {
                        return;
                    }
                    if(minPlan.size()/3 >= courier.getMaxLoads()){
                        return;
                    }
                    minPlan.get(minPlan.size()-1).setCost(minCost);
                    r.add(minPlan);
                }
        );
        return r;
    }
}

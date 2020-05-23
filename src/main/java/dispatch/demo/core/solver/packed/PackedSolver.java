package dispatch.demo.core.solver.packed;

import dispatch.demo.core.context.DispatchContext;
import dispatch.demo.core.route.PackedTrailPlaner;
import dispatch.demo.core.route.Planner;
import dispatch.demo.core.solver.Solver;
import dispatch.demo.dto.ActionNode;
import dispatch.demo.dto.Courier;
import dispatch.demo.dto.CourierPlan;
import dispatch.demo.dto.Order;
import lombok.Data;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.MutablePair;
/**
 * @author eleme.demo
 */

public class PackedSolver implements Solver {

    /** 两次调度最短间隔时间 */
    private static final long MINIMUM_INTERVAL_SECONDS = 60;
    protected  static  long maxUsedTime = 0;
    List<Order> orders;
    List<Courier> couriers;
    DispatchContext context;

    List<Order> ordersAssigned;


    /** 路径规划:新单永远规划在末尾 */
    Planner planner = new PackedTrailPlaner();
    public static ForkJoinPool forkJoinPool = new ForkJoinPool(8);
    public PackedSolver(DispatchContext context) {
        this.context = context;
        this.orders = getCandidateOrders(context);
        this.couriers = getCandidateCouriers(context);
        this.ordersAssigned = new ArrayList<>();
    }
    private final long candidateCouriersRange = MINIMUM_INTERVAL_SECONDS*10;
    protected List<Courier> getCandidateCouriers(DispatchContext dispatchContext) {
        return dispatchContext.getCourierPool().getCouriers().stream()
                .filter(x->x.getOrders().isEmpty()
                        ||x.getPlanRoutes().get(x.getPlanRoutes().size()-1).getActionTime() < context.getTimeStamp()+candidateCouriersRange)
                .collect(Collectors.toList());
    }

    protected List<Order> getCandidateOrders(DispatchContext dispatchContext) {

        List<Order> r =   dispatchContext.getOrderPool().getDispatchingOrders();
        r.sort((a,b)->-((int) (a.getPromiseDeliverTime()-b.getPromiseDeliverTime())));
        return r;
    }
    @Override
    public List<String> getAssignedOrderIds() {
        return ordersAssigned.stream().map(Order::getId).collect(Collectors.toList());
    }





    private List<List<RichAction>> generatePlanning(Courier courier,List<Order> orders,final int norder){
        List<List<RichAction>> r = new ArrayList<>();
        if(norder == 1){
            for(Order o : orders){
                List<RichAction> actions = new ArrayList<>(3);
                actions.add(new RichAction(null,o,1));
                actions.add(new RichAction(null,o,3));
                List<RichAction> temp = planner.plan(courier,new ArrayList<>(actions),this.context);
                assert temp.get(0) != null;
                temp.get(temp.size()-1).setCost(uitls.calculateCost(context,temp));
                r.add(temp);
            }

            return r;
        }





        if(norder == 2){
            List<List<RichAction>> r2 = PlanningUiils.planTwo(context, courier,orders,planner,1e-3 );
            return r2;
        }
        List<List<Order>> orderset = new ArrayList<>();
        long generateStartTime = System.currentTimeMillis();
        dfsOrderGenerating(orders,norder,0,new ArrayList<>(),orderset,new HashMap<>(),3);
        for( List<Order> subOrders : orderset){
            List<List<RichAction>> allPlans  = new ArrayList<>();
            dfsPlanning(subOrders.stream().map(x-> new MutablePair<Order,Integer>(x,0)).collect(Collectors.toList()), 0,new ArrayList<RichAction>(),allPlans);
            if(allPlans.isEmpty()){
                continue;
            }

            double minCost = Double.MAX_VALUE;
            List<RichAction> tempr = null;
            for (List<RichAction> actions : allPlans){

                actions = planner.plan(courier,actions,this.context);

                double c = uitls.calculateCost(context,actions);
                actions.get(actions.size()-1).setCost(c);
                if(c < minCost){
                    tempr = actions;
                    minCost = c;
                }
            }
            r.add(tempr);
        }

        return r;


    }

    private void dfsOrderGenerating(final List<Order> allOrders,final int targetLength,final int index,
                                    List<Order> buffer,final List<List<Order>> result,final Map<String,Integer> orderUsed,final int orderUsedLimit){
        if(targetLength == buffer.size()){
            List<Order> t = new ArrayList<>(buffer);
            result.add(t);
            for(Order order : buffer){
                orderUsed.put(order.getId(),orderUsed.getOrDefault(order.getId(),0)+1);
            }
            return;
        }
        for(int i=index;i < allOrders.size();i++){
            if(buffer.size() > 0 && orders.get(i).getSrcLoc().distanceTo(buffer.get(0).getSrcLoc()) > 0.0001*1e-5
                    && orders.get(i).getDstLoc().distanceTo(buffer.get(0).getDstLoc()) > 0.0001*1e-5
            ){
                continue;
            }
            if(orderUsed.getOrDefault(orders.get(i).getId(),0) > orderUsedLimit){
                continue;
            }
            buffer.add(orders.get(i));
            dfsOrderGenerating(allOrders,targetLength,i+1,buffer,result,orderUsed,orderUsedLimit);
            buffer.remove(buffer.size()-1);
        }
    }


    private void dfsPlanning(List<Pair<Order,Integer>> orders, int index, ArrayList<RichAction> buffer, List<List<RichAction>> result){
        if(index == orders.size()*2 && !orders.isEmpty()){
            assert !buffer.isEmpty();
            result.add(new ArrayList<>(buffer) );
            return;
        }
        for(Pair<Order,Integer> orderp : orders){
            Order order = orderp.getKey();
            int status = orderp.getValue();
            if(status == 2){
                continue;
            }
            int targetActionType = status == 0?1:3;
            orderp.setValue(status+1);
            buffer.add(new RichAction(null,order,targetActionType));
            dfsPlanning(orders,index+1,buffer,result);
            buffer.remove(buffer.size()-1);
            orderp.setValue(status);
        }
    }


    @Override
    public List<CourierPlan> solve() {
        Map<Courier,List<List<RichAction>>> courier2ActionList= new ConcurrentHashMap<>();
        final int maxPlaningOrder = 2;
        long startTime = System.currentTimeMillis();
        long planStartTime = System.currentTimeMillis();
        boolean flagOvertime = true;
        List<List<List<Order>>> groupOrder = PlanningUiils.groupOrderAll(orders,1e-3);
        List<List<List<RichAction>>> processedGroupOrder = PlanningUiils.processGroupedOrder(groupOrder,20);

        for(int iplanning=1;iplanning <= maxPlaningOrder;iplanning++){
            final long limitTIme = iplanning == 1?1400:1400;
            List<Pair<Courier,Future<List<List<RichAction>>>>> fs = new ArrayList<>();
            for(Courier courier : couriers){
                int finalIplanning = iplanning;
                Future<List<List<RichAction>>> f = forkJoinPool.submit(()->{
                    if(finalIplanning == 1 ){
                        return this.generatePlanning(courier,this.orders, finalIplanning);
                    }
                    List<List<RichAction>> couierPlan = null;
                    couierPlan= PlanningUiils.computeCost(context,courier,planner,processedGroupOrder);
                    return couierPlan;
                });
                fs.add(new ImmutablePair<>(courier,f));
            }
            for(int ifs=0;ifs < fs.size();ifs++){
                Pair<Courier,Future<List<List<RichAction>>>> p = fs.get(ifs);
                Courier courier = p.getKey();
                List<List<RichAction>> courierAction=null;
                try {
                    try {
                        courierAction = p.getValue().get(Math.max(0,limitTIme-(System.currentTimeMillis()-startTime)),TimeUnit.MILLISECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException | CancellationException e) {

                        flagOvertime=false;
                        for(int j=ifs;j < fs.size();j++){
                            if(!fs.get(j).getValue().isCancelled()){
                                fs.get(j).getValue().cancel(true);
                            }
                        }
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
                if(!flagOvertime){
                    break;
                }
                if(courierAction == null){
                    continue;
                }
                for(List<RichAction> t : courierAction){
                    assert (t.get(0) != null);
                }
                if(!courier2ActionList.containsKey(courier)){
                    courier2ActionList.put(courier,new ArrayList<>());
                }
                courier2ActionList.get(courier).addAll(courierAction);

            }
            if(!flagOvertime){
                break;
            }

        }
        long planEndTime = System.currentTimeMillis();


        Map<String,AtomicInteger[]> m = new ConcurrentHashMap<>();//int[2] [total,exceed limit]
        orders.stream().forEach((o)->{
            AtomicInteger[] x = new AtomicInteger[2];
            x[0] = new AtomicInteger(0);
            x[1] = new AtomicInteger(0);
            m.put(o.getId(),x);
        });
        try {
            forkJoinPool.submit(()->{
                courier2ActionList.entrySet().stream().parallel().forEach(
                        (e)->{
                            for(List<RichAction> actioneList : e.getValue()){
                                for(RichAction ra : actioneList){
                                    if(ra.getAction_type() != 3){
                                        continue;
                                    }
                                    if(ra.getActionNode().getActionTime() > ra.getOrder().getPromiseDeliverTime()){
                                        m.get(ra.getOrder().getId())[1].incrementAndGet();
                                    }
                                    m.get(ra.getOrder().getId())[0].incrementAndGet();
                                }
                            }
                        }
                );

            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

        try {
            forkJoinPool.submit(()->
            courier2ActionList.entrySet().stream().parallel().forEach(
                    (e)->{
                        long ctime = -1;
                        Courier courier = e.getKey();
                        if(courier.getPlanRoutes().isEmpty()){
                            ctime = context.getTimeStamp();
                        }
                        else {
                            ctime=courier.getPlanRoutes().get(courier.getPlanRoutes().size()-1).getActionTime();
                        }
                        for(List<RichAction> actions : e.getValue()){
                            float importrtance = 0;
                            int importanceFactor = 60 * 5;
                            float courierFactor = 8.0f ;
                            for(RichAction richAction : actions){
                                if(richAction.getAction_type() != 3){
                                    continue;
                                }
                                AtomicInteger data[] = m.get(richAction.getOrder().getId());
                                if((double)data[1].get()/(data[0].get()) > 0.7  ){
                                    importrtance++;
                                }
                            }
                            double preCost = actions.get(actions.size()-1).getCost();
                            if(importrtance != 0){
                                importrtance /= ((double)actions.size())/3 ;
                            }
                            long timeDelta = Math.max(Math.min(ctime-context.getTimeStamp(),60 * 2),-60*5);

                            actions.get(actions.size()-1).setCost(preCost - importanceFactor*importrtance + timeDelta* courierFactor);

                        }
                    }

            )
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

        AtomicInteger maxCandidateCount = new AtomicInteger();
        try {
            forkJoinPool.submit(()->
            courier2ActionList.entrySet().stream().parallel().forEach(
                (e)->{
                    Courier courier = e.getKey();
                    List<List<RichAction>> plans = e.getValue();
                    plans.sort((a,b)->{
                        double sign =  a.get(a.size()-1).getCost() - b.get(b.size()-1).getCost();
                        if(sign == 0){
                            return 0;
                        }
                        return sign<0?-1:1;
                    });
                    maxCandidateCount.set(Math.max(maxCandidateCount.get(), plans.size()));
                    final int maxCandidate = 200;
                    if(plans.size() > maxCandidate){
                        plans = plans.subList(0,maxCandidate);
                    }
                    e.setValue(plans);
                }
            )
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
        Set<String> usedCourer = new HashSet<>();
        Set<String> usedOrder = new HashSet<>();
        List<Integer> submmitLength = new ArrayList<>();
        List<CourierPlan> submitPlan = new ArrayList<>();


        List<Pair<Courier,List<RichAction>>> allplans = new ArrayList<>();
        for(Map.Entry<Courier,List<List<RichAction>>> e : courier2ActionList.entrySet()){
            for(List<RichAction> actions : e.getValue()){
                allplans.add(new ImmutablePair<>(e.getKey(),actions));
            }
        }
        allplans.sort((a,b)->{
            List<RichAction> a1 = a.getValue();
            List<RichAction> a2 = b.getValue();
            double sign =  a1.get(a1.size()-1).getCost() - a2.get(a2.size()-1).getCost();
            if(sign == 0){
                return 0;
            }
            return sign<0?-1:1;
        });
        for(Pair<Courier,List<RichAction>> p : allplans){
            List<RichAction> minActions =  p.getValue();
            Courier minCourier = p.getKey();
            if(usedCourer.contains(minCourier.getId())){
                continue;
            }
            boolean flag = true;
            for(RichAction action : minActions){
                if(usedOrder.contains(action.getOrder().getId())){
                    flag = false;
                    break;
                }
            }
            if(!flag){
                continue;
            }
            for(RichAction richAction : minActions){
                //assert !usedOrder.contains(richAction.getOrder().getId()): usedOrder.toString()+"==="+richAction.getOrder().getId();
                usedOrder.add(richAction.getOrder().getId());
            }
            assert !usedCourer.contains(minCourier.getId());
            usedCourer.add(minCourier.getId());
            CourierPlan _ = new CourierPlan();
            _.setPlanRoutes(minActions.stream().map(RichAction::getActionNode).collect(Collectors.toList()));
            _.setCourierId(minCourier.getId());
            if(_.getPlanRoutes().get(0).getNeedSubmitTime() <=  this.context.getTimeStamp() + MINIMUM_INTERVAL_SECONDS*1
//                    && orders.size() < this.couriers.size()*5
//            || orders.size() > this.couriers.size()*5 && ordersAssigned.size() < 8
//                    || true
            ){
                submitPlan.add(_);

                minCourier.setPlanRoutes(minActions.stream().map(RichAction::getActionNode).collect(Collectors.toList()));
                Set<Order> addOrders = new HashSet<>(minActions.stream().map(RichAction::getOrder).collect(Collectors.toList()));
                List<Order> addOrders_ = new ArrayList<>(addOrders);
                minCourier.setOrders(addOrders_);
                ordersAssigned.addAll(addOrders_);

                submmitLength.add(addOrders.size());

            }
        }


        maxUsedTime = Math.max(maxUsedTime,System.currentTimeMillis()-planStartTime);
        System.out.println(String.format("solve end plan Time %d total %d" +
                " max used time %d " +
                "",planEndTime-planStartTime,System.currentTimeMillis()-planStartTime,maxUsedTime));

//        System.out.println(String.format("nSubmit %d nOrder %d nCourer usedCourer %d ",submitPlan.size(),this.orders.size(),this.couriers.size(),usedCourer.size()));
//        System.out.println("subbmits "+ submmitLength.toString());
        return submitPlan;


    }


    private CourierPlan getSubmitPlan(Courier courier) {
        long submitThresholdTime = this.context.getTimeStamp() + MINIMUM_INTERVAL_SECONDS;
        List<ActionNode> submittedNodes = courier.getPlanRoutes().stream()
                .filter(node -> !node.isSubmitted())
                .filter(node -> node.getNeedSubmitTime() <= submitThresholdTime || context.isEndOfTest())
                .collect(Collectors.toList());
        CourierPlan plan = new CourierPlan();
        plan.setCourierId(courier.getId());
        plan.setPlanRoutes(submittedNodes);
        return plan;
    }

}

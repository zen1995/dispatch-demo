package dispatch.demo.core.route;

import dispatch.demo.core.context.DispatchContext;
import dispatch.demo.core.solver.packed.RichAction;
import dispatch.demo.dto.ActionNode;
import dispatch.demo.dto.Courier;
import dispatch.demo.dto.Location;
import dispatch.demo.dto.Order;
import dispatch.demo.utils.DistanceUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author eleme.demo
 * the courier always append new order to tail
 */
public class PackedTrailPlaner extends Planner {

    /**
     *
     * @param courier
     * @param actions
     * @param context
     * @return RichAction with action time
     */
    @Override
    public List<RichAction> plan(Courier courier, List<RichAction> actions, DispatchContext context) {
        Location loc;
        long planTime;
        if (courier.getOrders().isEmpty()) {
            loc = courier.getLoc();
            planTime = context.getTimeStamp();
        } else {
            int size = courier.getPlanRoutes().size();
            ActionNode lastNode = courier.getPlanRoutes().get(size - 1);
            Order lastOrder = context.getOrderPool().getOrderMap().get(lastNode.getOrderId());
            loc = lastOrder.getDstLoc();
            planTime = Math.max(lastNode.getActionTime(),context.getTimeStamp());
        }

        List<RichAction> r = executePlan(courier,loc,planTime,actions);
        return r;
    }

    private List<RichAction> executePlan(Courier courier, Location loc, long planTime,List<RichAction> actions){
        ArrayList<RichAction> r = new ArrayList<>();
        Location courierLocation = loc.clone();
        for( RichAction action : actions){
            Order order = action.getOrder();
//            long arrivalTime = planTime + DistanceUtils.timeConsuming(loc,action.get, courier.getSpeed());
            if(action.getAction_type() == 1){
                long arrivalTime = planTime + DistanceUtils.timeConsuming(courierLocation,action.getOrder().getSrcLoc(), courier.getSpeed()) ;
                long pickTime = Math.max(order.getEstimatedPrepareCompletedTime(), arrivalTime);
                ActionNode arrivalNode = new ActionNode(order.getId(), 1, arrivalTime, false, planTime);
                ActionNode pickNode = new ActionNode(order.getId(), 2, pickTime, false, arrivalTime);
//                RichAction r1 = new
                r.add(new RichAction(arrivalNode,action.getOrder(),1));
                r.add(new RichAction(pickNode,action.getOrder(),2));
                planTime = pickTime;
                courierLocation = action.getOrder().getSrcLoc();
                continue;
            }
            else if (action.getAction_type() == 3){
                long deliverTime = planTime + DistanceUtils.timeConsuming(courierLocation, order.getDstLoc(), courier.getSpeed());
                ActionNode deliveryNode = new ActionNode(order.getId(), 3, deliverTime, false, planTime);
                r.add(new RichAction(deliveryNode,action.getOrder(),3));
                planTime = deliverTime;
                courierLocation = action.getOrder().getDstLoc();

            }
            else{
                assert false;
            }
        }
        return r;
    }

}

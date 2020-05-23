package dispatch.demo.core.route;

import dispatch.demo.core.context.DispatchContext;
import dispatch.demo.core.solver.packed.RichAction;
import dispatch.demo.dto.ActionNode;
import dispatch.demo.dto.Courier;
import dispatch.demo.dto.Order;

import java.util.List;

/**
 * @author eleme.demo
 * route planner interface
 */
public abstract class Planner {

    /**
     * 路径规划
     *
     * @param courier   给定骑手
     * @param order     待规划的单
     * @param context   上下文信息
     * @return 骑手路径
     */
    public List<ActionNode> plan(Courier courier, Order order, DispatchContext context){
        assert false;
        return null;
    }
    public List<RichAction> plan(Courier courier, List<RichAction> actinos, DispatchContext context){
        assert false;
        return null;
    }

}

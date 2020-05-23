package dispatch.demo.core.solver.packed;

import dispatch.demo.dto.ActionNode;
import dispatch.demo.dto.Order;
import lombok.Data;

@Data
public class RichAction {
    int action_type = -1;
    ActionNode actionNode;
    double cost = -1;
    Order order;
    public RichAction(ActionNode actionNode,Order order,int action_type){
        this.actionNode = actionNode;
        this.order = order;
        this.action_type = action_type;
    }



}

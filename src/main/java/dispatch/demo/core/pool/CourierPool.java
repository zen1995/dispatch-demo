package dispatch.demo.core.pool;

import dispatch.demo.core.context.DispatchContext;
import dispatch.demo.dto.ActionNode;
import dispatch.demo.dto.Courier;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author eleme.demo
 */
@Data
public class CourierPool {
    private List<Courier> couriers = new ArrayList<>(0);
    public List<Courier> getAvailableCouriers(final DispatchContext context,final   long range){
        return couriers.stream().filter(
                x->{
                    if(x.getPlanRoutes().isEmpty()){
                        return true;
                    }
                    List<ActionNode> actionNodes = x.getPlanRoutes();
                    if (context.getTimeStamp() + range >= actionNodes.get(actionNodes.size() - 1).getActionTime()){
                        return true;
                    }
                    return false;
                }
        ).collect(Collectors.toList());
    }
}

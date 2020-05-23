package dispatch.demo.core.solver.packed;

import dispatch.demo.core.context.DispatchContext;

import java.util.List;

public class uitls {
    public static double calculateCost(DispatchContext context,List<RichAction> actions){
        final double timeFactor = 1;

        assert actions.size() % 3 == 0;
        final int norder = actions.size()/3;
        double totalCost =0;

        int iOrder = 0;
        long pretime = context.getTimeStamp();
        for(RichAction richAction : actions){
            if(richAction.getAction_type() != 3){
                continue;
            }
            iOrder+=1;
            double c = richAction.getActionNode().getActionTime()-pretime;//-richAction.getOrder().getCreateTime();
            totalCost += c;
            pretime = richAction.getActionNode().getActionTime();
            if(richAction.getActionNode().getActionTime() > richAction.getOrder().getPromiseDeliverTime()
//            && richAction.getActionNode().getActionTime() > richAction.getOrder().getPromiseDeliverTime()-60 * 10
            ){
                totalCost += 60 * 5;
            }
        }
        double r = (totalCost-(norder-1)*60*4*0) / norder;
//        actions.get(actions.size()-1).setCost(r);
        return r;//((norder+1)*norder/2);
    }
}

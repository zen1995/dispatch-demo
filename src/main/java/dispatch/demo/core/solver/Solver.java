package dispatch.demo.core.solver;

import dispatch.demo.dto.CourierPlan;

import java.util.List;

public interface Solver {
    public List<CourierPlan> solve();

    public List<String> getAssignedOrderIds();
}
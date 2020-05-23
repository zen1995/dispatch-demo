package dispatch.demo.controller;


import com.alibaba.fastjson.JSON;
import dispatch.demo.core.DispatchService;
import dispatch.demo.dto.DispatchRequest;
import dispatch.demo.dto.DispatchSolution;
import dispatch.demo.dto.Response;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * @author eleme.demo
 */
@RestController()
@RequestMapping("/api/v1")
public class DispatchController {


    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
    );

    DispatchService dispatchService = new DispatchService();


    @RequestMapping(value = "/ping", method = RequestMethod.GET)
    public Response<String> ping() {
        System.out.println("ping");
        return new Response<>("PONG");
    }

    @RequestMapping(value = "/score", method = RequestMethod.POST)
    public Response<String> score(HttpServletRequest request) {
        System.out.println("ping");
        return new Response<>("PONG");
    }
    long preTimestamp = System.currentTimeMillis();
    boolean prestatus = true;
    @RequestMapping(value = "/dispatch", method = RequestMethod.POST, produces = "application/json")
    public String dispatch(@RequestBody String jsonRequest) {
        long curTime = System.currentTimeMillis();
//        System.out.println(String.format("last used time %d",curTime-preTimestamp));
        preTimestamp = curTime;
        //        System.out.println(jsonRequest);
        DispatchRequest request = JSON.parseObject(jsonRequest, DispatchRequest.class);
        DispatchSolution result = null;
        Future<DispatchSolution> f = threadPoolExecutor.submit(() -> {
            return dispatchService.dispatch(request);
        });
        try {
            //wait maximum 4s
            result = f.get(4500, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            prestatus = false;
            e.printStackTrace();
            System.out.println(e.getMessage());
            //downgrade solution here, downgrade solution must finish within 1s so that total request processing will be finished within 5s.
            if(prestatus){
                prestatus = false;
                result = new DispatchSolution();
                result.setCourierPlans(new ArrayList<>());
                return JSON.toJSONString(new Response(200,result));

            }
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String msg=sw.toString();

            return JSON.toJSONString(Response.NewErrResponse(msg));
        }
        prestatus=true;
        if (null != result) {
//            System.out.println(JSON.toJSONString(result));
        }
        Response r = new Response(200, result);
        return JSON.toJSONString(r);
    }
}

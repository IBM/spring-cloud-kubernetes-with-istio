/*
 * Copyright © 2018 IBM Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

 package demo.k8s.k8sservice.controllers;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import java.io.*;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import java.util.stream.Collectors;
import java.util.List;
import demo.k8s.k8sservice.config.ConfigBean;
import demo.k8s.k8sservice.services.Reviews;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RestController
public class K8SRestController {

    private final AtomicLong counter = new AtomicLong();

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private ConfigBean config;

    @Value("${config.fish}")
    private String fish2;

    @Value("${spring.application.name}")
    private String appName;

    @Autowired
    private Reviews reviews;

    private String invokeUrlViaOkHttp(String url){
      return invokeUrlViaOkHttp(url,null);
    }
    private String invokeUrlViaOkHttp(String url, String hostName){
      try{
        OkHttpClient client = new OkHttpClient();

        Request request;
        if(hostName==null){
          request = new Request.Builder()
            .url(url)
            .build();
          }else{
            request = new Request.Builder()
              .url(url)
              .header("Host",hostName)
              .build();
          }

        Response response = client.newCall(request).execute();

        return (url+" :"+String.valueOf(hostName)+": "+response.code()+" :: ")+response.body().string();
      }catch(IOException io){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        io.printStackTrace(pw);
        String stack = sw.toString();
        stack.replaceAll("\n","<br>");
        return "Something bad happened<br>"+stack;
      }
    }

    @RequestMapping("/greeting")
    public String greeting(@RequestParam(value="name", defaultValue="World") String name,
                           @RequestParam(value="svc", defaultValue="reviews") String svcName) {

        List<ServiceInstance> si = discoveryClient.getInstances(svcName);
        String svcs = si.stream().map(a -> a.getUri().toString() ).collect(Collectors.toList()).toString();

        String message = reviews.getMessage();
        String message2 = invokeUrlViaOkHttp("http://reviews.default.svc.cluster.local:9080/health");
        String message3 = invokeUrlViaOkHttp( discoveryClient.getInstances(svcName).get(0).getUri().toString() + "/health", svcName);

        return "Hello "+name
               +"<br> count:"+counter.getAndIncrement()
               +"<br> fish via bean:"+config.getFish()
               +"<br> fish via restController:"+fish2
               +"<br> heelHeight via bean:"+config.getHeelHeight()
               +"<br> "+svcName+" svc via discovery: ("+si.size()+")"+svcs
               +"<br> appName:"+String.valueOf(appName)
               +"<br> all service names from discovery:"+discoveryClient.getServices()
               +"<br> invocation response : "+message
               +"<br> response of k8s fixed url invoke : "+message2
               +"<br> response of k8s via discoveryClient invoke : "+message3
                ;

    }
}

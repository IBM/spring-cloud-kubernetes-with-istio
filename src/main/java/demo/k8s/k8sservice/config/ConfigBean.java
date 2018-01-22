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

 package demo.k8s.k8sservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Configuration
@ConfigurationProperties(prefix = "config")
public class ConfigBean {

  private String fish;
  private String howHigh;

  public String getFish(){
    return fish;
  }

  public void setFish(String f){
    this.fish = f;
  }

  public String getHeelHeight(){
    return howHigh;
  }

  public void setHeelHeight(String h){
    this.howHigh = h;
  }
}

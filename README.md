# Spring Cloud Kubernetes & Istio.

## Summary

[Spring Cloud Kubernetes](https://github.com/spring-cloud-incubator/spring-cloud-kubernetes)
is a great way to use [Spring Cloud](http://projects.spring.io/spring-cloud/) concepts
 while running on [Kubernetes](https://kubernetes.io/). It offers mappings between Spring and Kubernetes
versions of concepts such as; service discovery, service proxying, and configuration.

But what happens when you throw [Istio](https://istio.io/) into the mix? Istio allows for content based
routing, fault injection, and more. Does Spring Cloud Kubernetes play well? or will
there be snaggles to work out?

## Testing Spring Cloud Kubernetes with Istio

### Environment

I used [IBM Cloud Private](https://github.com/IBM/deploy-ibm-cloud-private)
(ICP) 2.1.0 via Vagrant for my tests, with Istio 0.4.0 installed
with the [auto sidecar injection](https://istio.io/docs/setup/kubernetes/sidecar-injection.html#automatic-sidecar-injection)
enabled. If you want to try the project out, make sure you have `kubectl`
[configured to talk to your cluster](https://github.com/IBM/deploy-ibm-cloud-private#accessing-ibm-cloud-private),
and you have done the [required setup](https://www.ibm.com/support/knowledgecenter/en/SSBS6K_2.1.0/manage_images/configuring_docker_cli.html)
to allow you to push images to the docker repository. Lastly you'll need your
`/etc/hosts` file updated to have `mycluster.icp` pointed at your cluster ip.
(Usually `192.168.27.100`, unless you altered the Vagrantfile)

If you are not using ICP, you'll need to edit the pom.xml and alter the instances
of `mycluster.icp:8500/default/demo:latest` to point at the registry of your choice.

After installing Istio on the cluster, also [install the Istio bookinfo sample](https://istio.io/docs/guides/bookinfo.html#running-on-kubernetes).
This article uses the reviews service from the bookinfo sample as it's target
endpoint. If you have auto sidecar injection enabled, then it's just this
one command from the Istio install directory.

```
kubectl apply -f samples/bookinfo/bookinfo.yaml
```

>TIP: Don't use [Istio Mutual TLS](https://istio.io/docs/concepts/security/mutual-tls.html)
(`istio-auth.yaml`) with Spring Cloud Kubernetes at
the moment, Istio doesn't like the way that Spring Cloud Kubernetes will use the
same port for livenessProbe/readinessProbe, as it does for the main service.

The full code for the test app is available [here](TODO!), and can be built, and deployed
with

```
mvn package fabric8:build fabric8:resource fabric8:push fabric8:deploy
```

The maven pom file
relies upon the [Fabric8 Maven Plugin](https://maven.fabric8.io/) to generate the
docker image for the app, push the docker image to the registry, generate the
kubernetes deployment and service yaml, and apply them to the cluster.

You'll need to apply `ingress.yaml` to your cluster as well, and that will let you
invoke the application at `http://mycluster.icp/greeting`

```
kubectl apply -f ./ingress.yaml
```

### Config Maps via injection / Config Beans.

Spring Cloud Kubernetes can map properties from a single config map into your
application via  either `@Value` annotated fields, or via a bean annotated with
`@ConfigurationProperties` the latter accepting a `prefix` attribute that can
set the prefix under which the properties in the map. Eg, if the Config Map had
a property of `config.fish` and the annotation was `@ConfigurationProperties(prefix="config")`
then the bean can have fields of `String getFish()` and `void setFish(String value)`

Our example application uses both approaches, with the RestController itself
using `@Value` injection, and a bean class `ConfigBean` using the `@ConfigurationProperties`.
To try it out, apply the `configmap.yaml` to your cluster to create the map used
by the application.

```
kubectl apply -f ./configmap.yaml
```

And then access `http://mycluster.icp/greeting` and look for the
values of `fish` and `heelHeight` in the output. Notice how the configmap values
have overridden the values specified in `application.properties` in the application.

>TIP: if you create a map while the app is running, where the map was missing
before the app was started, you may need to restart the app to have it bind
correctly to the ConfigMap, rather than just use it's values in `application.properties`.
Restart the app by scaling the ReplicaSet to zero and back to 1, or by removing
the k8sservice Pod, and allowing the ReplicaSet to launch a new one for you.

From a build perspective, it's worth noting that to use this feature, you have to
add the dependency for the config feature to your pom.xml. This used to be included in the default
`spring-cloud-starter-kubernetes` feature back when it was owned by `fabric8`, but has
been split out to it's own feature now it's owned by `spring-cloud-incubator`.
```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-kubernetes-config</artifactId>
</dependency>
```

By default, Spring Cloud Kubernetes will look for a Kubernetes ConfigMap with the
same name as the application (set via the `spring.application.name` property in
application.properties/yaml), the map name can be altered by setting the properties
`spring.cloud.kubernetes.config.name` and `spring.cloud.kubernetes.config.namespace`
which work as expected, and allow the config to be pulled from a differently named map,
even from outside the namespace of the app.

For more information, check out the current documentation for this feature at the
[Spring Cloud Kubernetes GitHub](https://github.com/spring-cloud-incubator/spring-cloud-kubernetes#configmap-propertysource).

### Responding to ConfigMap Changes at Runtime

Spring Cloud Kubernetes has one extra trick for config, it can update the config
in the running app if the ConfigMap data changes during runtime. To enable the
live config updates, you need to add the property
```
spring.cloud.kubernetes.reload.enabled=true
```
to your application's `bootstrap.properties` file.

By default, changes are detected by monitoring Kubernetes Events, an approach
that requires the app to have the appropriate permissions, `view` for ConfigMaps,
and `edit` for Secrets. By default, ICP does not grant these, but you can add them to the
serviceaccount using kubectl, for example, to add the `view` role, you could do this:

>TIP: Because Istio deploys to clusters with Role Based Auth (RBAC) enabled,
it's a lot more likely you'll need to give the appropriate permissions for
event based config updates to function.

```
kubectl create rolebinding default-view-binding --clusterrole=view --serviceaccount=default:default --namespace=default
```
This works because our example is deployed in ICP using the serviceaccount `default` in the `default` namespace.

If you cannot grant the permissions, you could switch the config monitoring to `polling` mode,
by adding these properties to your `bootstrap.properties`
```
spring.cloud.kubernetes.reload.mode=polling
spring.cloud.kubernetes.reload.period=500
```

Once changes are being detected, you'll then notice that by default, changes to
config that are read via the `@ConfigurationProperties` annotated bean, are updated
in the app as you would expect, but changes read via the `@Value` annotated field
in RestController, are not picked up. Clearly Spring Cloud Kubernetes is aware the property
has changed, but the problem is that by default the RestController is not an entity
that will be reloaded after the config change.

You can try this with the example app, by altering the configmap.yaml and reissuing
the `kubectl apply` command to update the configmap in the cluster. When you access
`http://mycluster.icp/greeting` you will see the values 'via bean'
will have updated to reflect the new ConfigMap values. Note that the value 'via RestController'
remains unaffected.

If you need other objects to respond to the change, you can change the refresh
strategy for the reload, using the property `spring.cloud.kubernetes.reload.strategy`,
the value `restart_context` will relaunch the Spring ApplicationContext, or the value
`shutdown` will cause the container to exit (and relies upon the replication controller
in Kubernetes to sping up a new container that will pick up the new value).

The full documentation for the "PropertySource Reload" feature is over in the
[Spring Cloud Kubernetes GitHub](https://github.com/spring-cloud-incubator/spring-cloud-kubernetes#propertysource-reload).

### RestTemplate and Istio

Spring's RestTemplate is a really handy way to invoke another service, and when
running on Kubernetes, you can take advantage of the Kubernetes approach to Services
that gives every service a unique DNS name within the cluster.

Using a RestTemplate to access `http://servicename.servicenamespace.svc.cluster.local:serviceport/`
works as expected, with Spring going via the Kubernetes Service to reach an appropriate backing
service instance. If the Service in question is represented by a ReplicaSet with multiple
instances, Kubernetes handles the load balancing across the instances.

As expected, any requests made in this way gain any behaviors configured via
Istio for the service being invoked.

### Discovery Client and Istio

Spring Cloud Kubernetes provides an implementation of the Spring DiscoveryClient
that uses Kubernetes to find service instances. The discovery client is included
in the default `spring-cloud-starter-kubernetes` dependency, but for it to function
it's important to note you must add the `@EnableDiscoveryClient` annotation to your
main Spring Application class. Without this annotation, the injected DiscoveryClient
will have no Kubernetes logic, and won't behave as expected.

You can use the DiscoveryClient to lookup services, and get a list of all services
within your applications namespace. Note that the URI's returned for the
services are the Pod addresses, invoking a service via such a URI means your
are hitting the Pod directly, bypassing any LoadBalancing configured in Kubernetes.

This also has consequences for Istio, when you hit the Pod IP and Port directly,
Istio doesn't know which service you are trying to access, and is unable to apply
any of the the behavior you may have configured for the service. When Istio is
in play, you are not talking directly to the service, but instead to the envoy
proxy that Istio has deployed as the sidecar. The proxy requires the Http `Host:`
header to be set, with the name of the service being invoked. Without this header
today Istio will return a 404 for

### Client side load balancing with Ribbon and Istio

Similarly to DisoveryClient, using Spring Cloud Kubernetes Ribbon Plugin allows
Spring applications to perform client side load balancing.

Adding the Ribbon Plugin is as simple as adding the dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-netflix</artifactId>
</dependency>
```

It also needs the rest of Ribbon to work, so you need the following too..

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-ribbon</artifactId>
  <version>1.4.2.RELEASE</version>
</dependency>
```

Then you just annotate your RestTemplate with `@LoadBalanced`, and add
`@RibbonClient(name = "servicename")` to your main SpringApplication class,
where `servicename` matches the name of the Kubernetes Service you want to call.

Then when you use the rest template with a url like `http://servicename/`
(`servicename` matching the one you set in `@RibbonClient`),
Spring Cloud Kubernetes will go find the services matching that
name, and invoke one as per its load balancing configuration.

Note that because `RibbonClient` is essentially using the `DiscoveryClient` to find it's Services,
that it is hitting the Pod IP and Port directly. Unfortunately, it will not add a
`Host:` header with the service name, meaning that **requests made via Ribbon will
_bypass_ Istio**.

## Snaggles

### Istio within the same pod.

Istio is only able to affect traffic between pods, so if a service decides to
invoke itself, or any other endpoint hosted within the same Pod Istio will not be able to affect the traffic.

Imagine multiple RestControllers, or multiple RequestMappings on a RestController,
where one invocation attempts to invoke itself, or a invokes a different path that's part of the same app.
This should hopefully be rare, as the app could just invoke the other path directly as a Java method,
rather than going via Rest, just be aware that in either case, Istio will be unable to affect the
call.

If you require Istio behavior in these circumstances, you should be sure to invoke the Kubernetes Service
URL, ( `http://servicename.servicenamespace.svc.cluster.local`) rather than a URL obtained via DiscoveryClient or via Ribbon.

### Fabric8 Maven Plugin issues.

Istio requires the ports for the container to be named as `grpc`,`redis`,`mongo`, `http` or `http2`.
As per the Istio [Pod Spec Requeirements](https://istio.io/docs/setup/kubernetes/sidecar-injection.html#pod-spec-requirements))
Fabric8 Maven Plugin attempts to name the port based on the port number. For `8080`
Fabric8 Maven Plugin knows it should assign the name `http`, but for port `9080`
(another common port used by well known Java app servers for http), it will assign the name `glrpc`.

Having a port name other than one of the Istio recognized portnames, results in Istio not affecting
the traffic at all. If your Istio rules are not applying as you would expect, remember to check the
port names.

>NOTE: If the port names do not match between the container and the service, an exception is thrown,
and the connection is refused. This one caught me out for a while!

I've yet to figure out how to convince Fabric8 Maven Plugin to allow me to define names for ports, so
*if you plan to use this plugin with Istio, ensure you use port 80, 443, or 8080* to have the expected results.

### Istio initial startup networking

Because I'm testing with ICP, which I'm running inside a Vagrant VM, and because I wasn't
paying attention to the VM's resource requirements, I ended up with a rather overstretched VM.
This in return lead to slow container startup times.

There's a brief period during App Container start with Istio, where the Istio Proxy takes over
the network traffic for the App, during which requests to/from the pod can be refused.

If your cluster is experiencing heavy load, or is being run by an uncaring administrator inside
an overstretched VM, then that window can be long enough to affect Spring Cloud Kubernetes applications.

During startup for a Spring Cloud Kubernetes application, the library attempts to make requests
to the Kubernetes API via http to obtain information about the Pod it is running in. If that
request occurs at a point when the Pods networking isn't functional, you'll see a very early
stack trace in the logs for the App, and lookups via DiscoveryClient/RibbonClient or ConfigMap
value injection, may not act as expected.

My workaround was to add an initial delay to the app container by prepending a `sleep 15` to the
Docker `CMD` instruction for the container. This gave Istio a chance to get the networking sorted
out before my app attempted to use it.

Chances are you won't need the delay, and will hopefully never notice it, but if you do, maybe
knowing this will help =). (Alternatively maybe Spring Cloud Kubernetes could retry it's request
using hystrix, or similar, or swap to an async/reactive model for handling the reply).

## Testing Istio Rules with the Application.

Included within the example project is an Istio yaml that instructs Istio to return Error 500 for
all calls to the review service.

If you add this rule via `istioctl` ...

```
istioctl create -f ./reviews-500.yaml
```

You will then see when you access `http://mycluster.icp/greeting` that the calls placed to the `reviews`
service are now returning Error 500. The 3 invocations made are:

- Via a RestTemplate to the K8S Service URL.
- Via a direct HTTP client to the K8S Service URL.
- Via a direct HTTP client to the URL retrieved from DiscoveryClient, but with an added `Host:` header.

>TIP: if you have followed the bookinfo tutorial, you will have rules in place that
have a higher precedence than the error 500 rule, either delete the other rules for the
review service, or edit the `reviews-500.yaml` to give it a higher precedence.

## Conclusion

If you want to use Istio, you probably don't want to be using `RibbonClient` within your
Spring application when using Spring Cloud Kubernetes. You'll lose most of the power of Istio,
and it may not be immediately obvious what's going on.

Use of DiscoveryClient results should be performed cautiously, the requirement to add the
`Host:` header is unlikely to be met by existing code, or code within Libraries, leading to
unexpected effects when the application is reliant upon Istio for routing rules, or custom
behavior.

ConfigMap integration on the other hand works very well, and does represent a great way to
integrate Kubernetes config to a Spring app. Just bear in mind it only allows one ConfigMap, so
if your app requires multiple, then you might as well configure them via the Kubernetes yaml to
use `envFrom` to pull the values into the container environment.

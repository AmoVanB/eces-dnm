# DNM

The *deterministic network modeling* (DNM) module implements different network models for access control and delay guarantees to be used by the [routing module](https://github.com/AmoVanB/eces-routing) of the [ECES](https://github.com/AmoVanB/eces-core) framework.
This allows to use the routing module for finding paths with strict delay guarantees in communication networks.

The logic of the models and the implementation rely on *deterministic network calculus* concepts.
See our [technical report](https://mediatum.ub.tum.de/doc/1328613/file.pdf) about the topic and the [main reference](https://infoscience.epfl.ch/record/282/files/netCalBookv4.pdf) defining and describing network calculus concepts. 

This repository corresponds to the reference implementation for the **Chameleon** and **DetServ** models described in:
- [Amaury Van Bemten, Nemanja Ðerić, Amir Varasteh, Stefan Schmid, Carmen Mas-Machuca, Andreas Blenk, and Wolfgang Kellerer. "Chameleon: Predictable Latency and High Utilization with Queue-Aware and Adaptive Source Routing." ACM CoNEXT, 2020](https://mediatum.ub.tum.de/doc/1577772/file.pdf), and
- [Jochen W. Guck, Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS provisioning in SDN-based industrial environments." IEEE TNSM, 2017](https://mediatum.ub.tum.de/doc/1420159/file.pdf),

and also implements the state-of-the-art **QJump** and **Silo** models described in:
- [Matthew P. Grosvenor, Malte Schwarzkopf, Ionel Gog, Robert NM Watson, Andrew W. Moore, Steven Hand, and Jon Crowcroft. "Queues Don’t Matter When You Can JUMP Them!." USENIX NSDI, 2015](https://www.usenix.org/system/files/conference/nsdi15/nsdi15-paper-grosvenor.pdf), and
- [Keon Jang, Justine Sherry, Hitesh Ballani, and Toby Moncaster. "Silo: Predictable message latency in the cloud." ACM SIGCOMM, 2015](https://people.eecs.berkeley.edu/~justine/silo_final.pdf).

This mostly comes in the form of different [Proxy](https://github.com/AmoVanB/eces-routing/blob/master/src/main/java/de/tum/ei/lkn/eces/routing/proxies/Proxy.java) subclasses (see the [routing module](https://github.com/AmoVanB/eces-routing) for the description of what is a proxy) which implement different access control strategies.

The proxies require the existence of a [NCRequestData](src/main/java/de/tum/ei/lkn/eces/dnm/NCRequestData.java) instance attached to the same entity as the [Request](https://github.com/AmoVanB/eces-routing/blob/master/src/main/java/de/tum/ei/lkn/eces/routing/requests/Request.java) object.

## Usage

The project can be downloaded from maven central using:
```xml
<dependency>
  <groupId>de.tum.ei.lkn.eces</groupId>
  <artifactId>dnm</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

## Implemented Models

We currently have two models, i.e., two proxies, implemented.

### QJump Proxy

The [QJumpProxy](src/main/java/de/tum/ei/lkn/eces/dnm/proxies/QJumpProxy.java) implements the access control of the _QJump_ system.

The configuration of QJump (number of hosts, link rate, packet size and cumulative processing time) is assumed to be stored in a [QJumpConfig](src/main/java/de/tum/ei/lkn/eces/dnm/config/QJumpConfig.java) object attached to the same entity as the [Graph](https://github.com/AmoVanB/eces-graph/blob/master/src/main/java/de/tum/ei/lkn/eces/graph/Graph.java) object on which routing is to be performed.

### DetServ Proxy

The [DetServProxy](src/main/java/de/tum/ei/lkn/eces/dnm/proxies/DetServProxy.java) implements the access control of the _Chameleon_ and _DetServ_ models.

It is based on a configuration assumed to be stored as an instance of the [DetServConfig](src/main/java/de/tum/ei/lkn/eces/dnm/config/DetServConfig.java) object attached to the same entity as the [Graph](https://github.com/AmoVanB/eces-graph/blob/master/src/main/java/de/tum/ei/lkn/eces/graph/Graph.java) object on which routing is to be performed.

The configuration object consists of the following elements:

 * __Access control model:__ this is either the multi-hop model (MHM) or the threshold-based model (TBM) - see the [DetServ paper](https://mediatum.ub.tum.de/doc/1420159/file.pdf). _Chameleon_ corresponds to the TBM. In a nutshell, the multi-hop model assigns a maximum burst and rate to each queue while the threshold-based model assigns a maximum delay to each queue.
 * __Cost model:__ cost function for a given queue. This can be defined using the classes deriving from [CostModel](src/main/java/de/tum/ei/lkn/eces/dnm/config/costmodels/CostModel.java). For example, 
 ```java
 CostModel costFunction = new LowerLimit(new UpperLimit(new Division(new Constant(), new Summation(new Constant(), new QueuePriority())), 1), 0);
 ```
 defines a cost function of *1/(1+p)* bounded between 0 and 1 and where *p* is the priority of the queue.
 * __Burst increase model:__ along its path, a flow see its burst increasing. There are different ways of taking this into account: neglecting it, taking the worst-case delay (request deadline) as worst-case burst increase and taking the real burst increase (but then routing becomes sub-optimal, see our [ICC paper](https://arxiv.org/pdf/1805.11586;Routing) about that). See the [DetServ paper](https://mediatum.ub.tum.de/doc/1420159/file.pdf) for more information on this.
 * __Input link shaping (ILS):__ whether or not we use ILS. See the [DetServ paper](https://mediatum.ub.tum.de/doc/1420159/file.pdf) for more information on this. This is just a modeling change in order to be less conservative. It however increases runtime and makes the routing problem an M1 problem (see our [ICC paper](https://arxiv.org/pdf/1805.11586;Routing) about that).
 * __Residual mode:__ Within the assumption of sub-additive arrival curves and super-additive service curves, there are different ways of computing the residual rate latency service curve from an arrival curve and a service curve. Since they can both have several knee points, the residual service curve can also have multiple knee points, but because the arrival curve (resp. service curve) is assumed to be sub-additive (resp. super-additive), the residual service curve will be super-additive. From this, there are different ways of transforming a super-additive service curve in a rate-latency curve depending on which slope of the curve is used for the rate-latency one: the highest slope, the least one (then the least latency also) or the real curve (which is then not a rate-latency curve). **Note that, for networks with uniform link rates, this has no influence**.
 * __Maximum packet size:__ max packet size in the network (this defaults to 1530 bytes).
 * __Resource allocation:__ the MHM and TBM need resources (either rate/burst or delay) to be allocated to each queue in the network. A [SelectResourceAllocation](src/main/java/de/tum/ei/lkn/eces/dnm/resourcemanagement/resourceallocation/SelectResourceAllocation.java) object defines a given resource allocation algorithm (subclass of [ResourceAllocation](src/main/java/de/tum/ei/lkn/eces/dnm/resourcemanagement/resourceallocation/ResourceAllocation.java)) per scheduler, i.e., per physical unidirectional link.

The proxy also implements the _Silo_ model.
Indeed, Silo is a particular instance of the TBM model with real burst increase computation, no input link shaping, a shortest path cost function and using the [TBMSiloDefaultAllocation](src/main/java/de/tum/ei/lkn/eces/dnm/resourcemanagement/resourceallocation/TBM/TBMSiloDefaultAllocation.java) default resource allocation for each scheduler.

#### Components used by the DetServ Proxy

For its implementation, the DetServ proxy attaches, to each queue, a service model ([QueueModel](src/main/java/de/tum/ei/lkn/eces/dnm/queuemodels/QueueModel.java)) and an input model ([ResourceUtilization](src/main/java/de/tum/ei/lkn/eces/dnm/inputmodels/ResourceUtilization.java)). The former models the service offered by a queue (simply, its service curve) and the latter the traffic entering the queue (simply, its arrival curve).

For the MHM, the [QueueModel](src/main/java/de/tum/ei/lkn/eces/dnm/queuemodels/QueueModel.java) is extended with the maximum token bucket that can be accepted at this queue ([MHMQueueModel](src/main/java/de/tum/ei/lkn/eces/dnm/queuemodels/MHMQueueModel.java)).

Both MHM and TBM use a simple [TokenBucketUtilization](src/main/java/de/tum/ei/lkn/eces/dnm/inputmodels/TokenBucketUtilization.java) component to model the arrival curve at a given queue.
When ILS is enabled, both models then use a [PerInEdgeTokenBucketUtilization](src/main/java/de/tum/ei/lkn/eces/dnm/inputmodels/PerInEdgeTokenBucketUtilization.java) component, which simply keeps track of the token bucket arrival curves per incoming edge (if the flow starts at the given edge, this current edge is used as "incoming edge" label).

## DNM System

The DNM system is used by some specific model configurations to automate some actions and automatically update state information.
For example, it automatically allocate resources when a new scheduler is created.
Also, it automatically updates the service curves when a new flow is added.

## Examples

The Silo model can be configured in the following way:

```java
DetServConfig modelConfig = new DetServConfig(
                ACModel.ThresholdBasedModel,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                false,
                new Constant(), 
                (controller, scheduler) -> new TBMSiloDefaultAllocation(controller));
```

That config (or any other) must then be attached to the subject graph and initialized with the used controller:

```java
modelingConfigMapper.attachComponent(myNetwork.getQueueGraph(), modelConfig);
modelConfig.initCostModel(controller);
```

The routing algorithms in use must then be configured with a single proxy instance:

```java
DetServProxy proxy = new DetServProxy(controller);
algorithm1.setProxy(proxy);
algorithm2.setProxy(proxy);
...
algorithmN.setProxy(proxy);
```

and then a traditional routing request with the additional [NCRequestData](src/main/java/de/tum/ei/lkn/eces/dnm/NCRequestData.java) object will trigger a routing + admission control + registration run:

```java
Entity entity = controller.createEntity();
try (MapperSpace mapperSpace = controller.startMapperSpace()) {
        requestMapper.attachComponent(entity, new UnicastRequest(h1.getQueueNode(), h3.getQueueNode()));
        ncRequestDataMapper.attachComponent(entity, new NCRequestData(
                CurvePwAffine.getFactory().createTokenBucket(flowRate, flowBurst),
                Num.getFactory().create(deadline)););
        selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
}
```

See other ECES repositories using this library (e.g., the [tenant manager](https://github.com/AmoVanB/eces-tenant-manager)) for more detailed/advanced examples.

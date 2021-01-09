package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.core.MapperSpace;
import de.tum.ei.lkn.eces.dnm.config.ACModel;
import de.tum.ei.lkn.eces.dnm.config.BurstIncreaseModel;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.Division;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.LowerLimit;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.Summation;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.UpperLimit;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.Constant;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.QueuePriority;
import de.tum.ei.lkn.eces.dnm.exceptions.DNMException;
import de.tum.ei.lkn.eces.dnm.mappers.*;
import de.tum.ei.lkn.eces.dnm.proxies.DetServProxy;
import de.tum.ei.lkn.eces.dnm.queuemodels.MHMQueueModel;
import de.tum.ei.lkn.eces.dnm.queuemodels.QueueModel;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.MHM.MHMRateRatiosAllocation;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM.TBMSiloDefaultAllocation;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM.TBMStaticDelaysAllocation;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.graph.GraphSystem;
import de.tum.ei.lkn.eces.network.*;
import de.tum.ei.lkn.eces.network.mappers.DelayMapper;
import de.tum.ei.lkn.eces.network.mappers.SchedulerMapper;
import de.tum.ei.lkn.eces.network.util.NetworkInterface;
import de.tum.ei.lkn.eces.routing.RoutingSystem;
import de.tum.ei.lkn.eces.routing.SelectedRoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.RoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.csp.unicast.cbf.CBFAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.mcsp.astarprune.AStarPruneAlgorithm;
import de.tum.ei.lkn.eces.routing.mappers.PathMapper;
import de.tum.ei.lkn.eces.routing.mappers.RequestMapper;
import de.tum.ei.lkn.eces.routing.mappers.SelectedRoutingAlgorithmMapper;
import de.tum.ei.lkn.eces.routing.requests.UnicastRequest;
import de.tum.ei.lkn.eces.topologies.NetworkTopology;
import de.tum.ei.lkn.eces.topologies.networktopologies.GridRandom;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.curves.ServiceCurve;
import de.uni_kl.cs.discodnc.curves.dnc.ServiceCurve_DNC;
import de.uni_kl.cs.discodnc.misc.Pair;
import de.uni_kl.cs.discodnc.nc.bounds.Bound;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.*;

public class ModelsTest {
    private Controller controller;
    private Mapper<Scheduler> schedulerMapper;
    private Mapper<QueueModel> queueModelMapper;
    private Mapper<MHMQueueModel> MHMQueueModelMapper;
    private Mapper<Delay> delayMapper;
    private DetServConfigMapper modelingConfigMapper;
    private GraphSystem graphSystem;
    private NetworkingSystem networkingSystem;
    private RequestMapper requestMapper;
    private SelectedRoutingAlgorithmMapper selectedRoutingAlgorithmMapper;
    private NCRequestDataMapper ncRequestDataMapper;
    private PathMapper pathMapper;
    private TokenBucketUtilizationMapper tokenBucketUtilizationMapper;
    private PerInEdgeTokenBucketUtilizationMapper perInEdgeTokenBucketUtilizationMapper;

    @Before
    public void setup() {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
        Logger.getLogger("de.tum.ei.lkn.eces.core").setLevel(Level.OFF);

        controller = new Controller();

        // Systems
        graphSystem = new GraphSystem(controller);
        networkingSystem = new NetworkingSystem(controller, graphSystem);

        // Mappers
        schedulerMapper = new SchedulerMapper(controller);
        queueModelMapper = new QueueModelMapper(controller);
        delayMapper = new DelayMapper(controller);
        modelingConfigMapper = new DetServConfigMapper(controller);
        MHMQueueModelMapper = new MHMQueueModelMapper(controller);
        requestMapper = new RequestMapper(controller);
        selectedRoutingAlgorithmMapper = new SelectedRoutingAlgorithmMapper(controller);
        ncRequestDataMapper = new NCRequestDataMapper(controller);
        pathMapper = new PathMapper(controller);
        tokenBucketUtilizationMapper = new TokenBucketUtilizationMapper(controller);
        perInEdgeTokenBucketUtilizationMapper = new PerInEdgeTokenBucketUtilizationMapper(controller);
    }

    @Test
    public void detServMHMSuppMaterialTest() {
        /*
         * This test corresponds to the supplementary material of the DetServ TNSM paper.
         *
         * We just recreate the same scenario, add the same flows and make sure that resources are allocated in the
         * same way, that flows are appropriately rejected/accepted and that resources are appropriately reserved as
         * computed in the paper.
         */
        DetServConfig modelConfig = new DetServConfig(
                ACModel.MHM,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                false,
                new LowerLimit(new UpperLimit(
                        new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                        1), 0),
                (controller1, scheduler) -> new MHMRateRatiosAllocation(controller1, new double[]{1.0/2, 1.0/4, 1.0/8}));

        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm cbf = new CBFAlgorithm(controller);
        cbf.setProxy(proxy);
        modelConfig.initCostModel(controller);

        // Create network
        Network network = networkingSystem.createNetwork();
        modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

        NetworkNode h1 = networkingSystem.createNode(network);
        NetworkNode h2 = networkingSystem.createNode(network);
        Link link = networkingSystem.createLinkWithPriorityScheduling(h1, h2, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

        // Checking resource allocation
        for(Edge linkEdge : network.getLinkGraph().getEdges()) {
            Scheduler scheduler = schedulerMapper.get(linkEdge.getEntity());
            Queue[] queues = scheduler.getQueues();
            for(int i = 0; i < queues.length; i++) {
                MHMQueueModel queueModel = MHMQueueModelMapper.get(queues[i].getEntity());
                Delay queueDelay = delayMapper.get(queues[i].getEntity());

                double r, b, d, R, T;
                switch(i) {
                    case 0:
                        r = 500000000 / 8;
                        b = 298470;
                        d = 2.41224 / 1000;
                        T = 0.02448 / 1000;
                        R = 1000000000 / 8;
                        break;

                    case 1:
                        r = 250000000 / 8;
                        b = 149235;
                        d = 7.21224 / 1000;
                        T = 4.82448 / 1000;
                        R = 500000000 / 8;
                        break;

                    case 2:
                        r = 125000000 / 8;
                        b = 74617.5;
                        d = 16.81224 / 1000;
                        T = 0.01442448;
                        R = 250000000 / 8;
                        break;

                    default:
                        fail();
                        return;
                }

                assertEquals(CurvePwAffine.getFactory().createTokenBucket(r, b), queueModel.getMaximumTokenBucket());
                assertEquals(CurvePwAffine.getFactory().createRateLatency(R, T), queueModel.getServiceCurve());
                assertEquals(d, queueDelay.getDelay(), 1e-9);
            }
        }

        // Checking access control and (de)-registration

        // Adding the already existing flow in the example of the paper
        UnicastRequest existingFlowRequest = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        double deadline = 10.0 / 1000; // so that third queue is too slow (cost will the force to use 2nd queue)
        ArrivalCurve tb = CurvePwAffine.getFactory().createTokenBucket( 106115000.0 / 8, 45000);
        NCRequestData ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        Entity entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, existingFlowRequest);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[1], pathMapper.get(entity).getPath()[0]); // should be middle priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(106115000.0 / 8, 45000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Filling first queue because it shouldn't be used by the routing algorithm
        UnicastRequest fillFirstQueueRequest = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 5.0 / 1000; // so that goes to first queue
        tb = CurvePwAffine.getFactory().createTokenBucket( 500000000 / 8, 298470);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, fillFirstQueueRequest);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]); // should be highest priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(500000000 / 8, 298470), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(106115000.0 / 8, 45000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Adding the f1 flow which should be refused
        UnicastRequest f1 = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 10.0 / 1000; // so that third queue is too slow (cost will the force to use 2nd queue)
        tb = CurvePwAffine.getFactory().createTokenBucket( 100000000.0 / 8, 150000);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, f1);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertTrue(!pathMapper.isIn(entity)); // should be refused
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(500000000 / 8, 298470), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(106115000.0 / 8, 45000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Adding the f2 flow which should also be refused
        UnicastRequest f2 = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 10.0 / 1000; // so that third queue is too slow (cost will the force to use 2nd queue)
        tb = CurvePwAffine.getFactory().createTokenBucket( 200000000.0 / 8, 20000);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, f2);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertTrue(!pathMapper.isIn(entity)); // should be refused
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(500000000 / 8, 298470), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(106115000.0 / 8, 45000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Adding the f3 flow which should be accepted
        UnicastRequest f3 = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 10.0 / 1000; // so that third queue is too slow (cost will the force to use 2nd queue)
        tb = CurvePwAffine.getFactory().createTokenBucket( 130000000.0 / 8, 15000);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, f3);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertTrue(pathMapper.isIn(entity)); // should be accepted
        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[1], pathMapper.get(entity).getPath()[0]); // should be middle priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(500000000 / 8, 298470), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(106115000.0 / 8 + 130000000.0 / 8, 45000 + 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Removing flows to check deregistration
        requestMapper.detachComponent(fillFirstQueueRequest);
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(106115000.0 / 8 + 130000000.0 / 8, 45000 + 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(existingFlowRequest);
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(130000000.0 / 8, 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(f2); // this shouldn't change anything as it was not accepted
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(130000000.0 / 8, 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(f3);
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
    }

    @Test
    public void detServTBMSuppMaterialTest() {
        /*
         * This test corresponds to the supplementary material of the DetServ TNSM paper.
         *
         * We just recreate the same scenario, add the same flows and make sure that resources are allocated in the
         * same way, that flows are appropriately rejected/accepted and that resources are appropriately reserved as
         * computed in the paper.
         */
        DetServConfig modelConfig = new DetServConfig(
                ACModel.TBM,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                false,
                new LowerLimit(new UpperLimit(
                        new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                        1), 0),
                (controller1, scheduler) -> new TBMStaticDelaysAllocation(controller1, new double[]{1.74/1000, 6.6/1000, 11.22/1000}));

        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm cbf = new CBFAlgorithm(controller);
        cbf.setProxy(proxy);
        modelConfig.initCostModel(controller);

        // Create network
        Network network = networkingSystem.createNetwork();
        modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

        NetworkNode h1 = networkingSystem.createNode(network);
        NetworkNode h2 = networkingSystem.createNode(network);
        Link link = networkingSystem.createLinkWithPriorityScheduling(h1, h2, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

        // Checking resource allocation
        for(Edge linkEdges : network.getLinkGraph().getEdges()) {
            Scheduler scheduler = schedulerMapper.get(linkEdges.getEntity());
            Queue[] queues = scheduler.getQueues();
            for(int i = 0; i < queues.length; i++) {
                Delay queueDelay = delayMapper.get(queues[i].getEntity());

                double delay;
                switch(i) {
                    case 0:
                        delay = 1.74/1000;
                        break;

                    case 1:
                        delay = 6.6/1000;
                        break;

                    case 2:
                        delay = 11.22/1000;
                        break;

                    default:
                        fail();
                        return;
                }

                assertEquals(queueDelay.getDelay(), delay);
            }
        }

        // Checking access control and (de)-registration

        // Adding the already existing flow in the example of the paper (high priority)
        UnicastRequest existingHighPriorityFlowRequest = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        double deadline = 2.0 / 1000; // so that only high priority queue can accept
        ArrivalCurve tb = CurvePwAffine.getFactory().createTokenBucket( 322000000.0 / 8, 186000);
        NCRequestData ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        Entity entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, existingHighPriorityFlowRequest);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]); // should be high priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(322000000.0 / 8, 186000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Adding the already existing flow in the example of the paper (middle priority)
        UnicastRequest existingMiddlePriorityFlowRequest = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 7.0 / 1000; // so that low priority cannot
        tb = CurvePwAffine.getFactory().createTokenBucket( 275000000.0 / 8, 195000);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, existingMiddlePriorityFlowRequest);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[1], pathMapper.get(entity).getPath()[0]); // should be middle priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(322000000.0 / 8, 186000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(275000000.0 / 8, 195000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Adding the already existing flow in the example of the paper (middle priority)
        UnicastRequest existingLowPriorityFlowRequest = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 100.0 / 1000; // so that low priority can
        tb = CurvePwAffine.getFactory().createTokenBucket( 93000000.0 / 8, 90000);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, existingLowPriorityFlowRequest);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[2], pathMapper.get(entity).getPath()[0]); // should be low priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(322000000.0 / 8, 186000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(275000000.0 / 8, 195000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(93000000.0 / 8, 90000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Adding the f1 flow which should be refused
        UnicastRequest f1 = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 10.0 / 1000; // so that third queue is too slow (cost will the force to use 2nd queue)
        tb = CurvePwAffine.getFactory().createTokenBucket( 81000000.0 / 8, 5500); // slightly lower rate than in paper to account for the fact that we don't consider packet size here
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, f1);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertTrue(!pathMapper.isIn(entity)); // should be refused
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(322000000.0 / 8, 186000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(275000000.0 / 8, 195000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(93000000.0 / 8, 90000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());


        // Adding the f2 flow which should be accepted
        UnicastRequest f2 = new UnicastRequest(h1.getQueueNode(), h2.getQueueNode());
        deadline = 10.0 / 1000; // so that third queue is too slow (cost will the force to use 2nd queue)
        tb = CurvePwAffine.getFactory().createTokenBucket( 30000000.0 / 8, 15000);
        ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

        entity = controller.createEntity();
        try (MapperSpace mapperSpace = controller.startMapperSpace()) {
            requestMapper.attachComponent(entity, f2);
            ncRequestDataMapper.attachComponent(entity, ncRequestData);
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
        }

        assertTrue(pathMapper.isIn(entity)); // should be accepted
        assertEquals(1, pathMapper.get(entity).getPath().length);
        assertEquals(link.getQueueEdges()[1], pathMapper.get(entity).getPath()[0]); // should be middle priority edge
        // Checking resource reservation
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(322000000.0 / 8, 186000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(275000000.0 / 8 + 30000000.0 / 8, 195000 + 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(93000000.0 / 8, 90000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());

        // Removing flows to check deregistration
        requestMapper.detachComponent(existingHighPriorityFlowRequest);
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(275000000.0 / 8 + 30000000.0 / 8, 195000 + 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(93000000.0 / 8, 90000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(existingMiddlePriorityFlowRequest);
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(30000000.0 / 8, 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(93000000.0 / 8, 90000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(existingLowPriorityFlowRequest);
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(30000000.0 / 8, 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(f1); // nothing should happen as it was not accepted
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(30000000.0 / 8, 15000), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
        requestMapper.detachComponent(f2); // nothing should happen as it was not accepted
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[0].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[1].getEntity()).getTokenBucket());
        assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link.getQueueEdges()[2].getEntity()).getTokenBucket());
    }

    @Test
    public void LokoPaperSiloForZodiacTest() {
        /*
         * This test corresponds to the motivation section of the Loko NSDI paper.
         *
         * We just check that resources are allocated as computed in the papeR.
         */
        DetServConfig modelConfig = new DetServConfig(
                ACModel.TBM,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                false,
                new LowerLimit(new UpperLimit(
                        new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                        4), 1),
                (controller1, scheduler) -> new TBMSiloDefaultAllocation(controller1));

        // Systems
        new DNMSystem(controller);
        new DetServProxy(controller);

        // Create network
        Network myNetwork = networkingSystem.createNetwork();
        modelingConfigMapper.attachComponent(myNetwork.getQueueGraph(), modelConfig);
        // 100 Mbps and queue size of Zodiac for one port and 306 packet size
        NetworkTopology networkTopology = new GridRandom(networkingSystem, myNetwork, 6, 6, 12500000, 0, new double[]{3*306});

        for(Edge link : networkTopology.getNetwork().getLinkGraph().getEdges()) {
            Scheduler scheduler = schedulerMapper.get(link.getEntity());
            Queue[] queues = scheduler.getQueues();
            for(int i = 0; i < queues.length; i++) {
                Delay queueDelay = delayMapper.get(queues[i].getEntity());

                double delay;
                switch(i) {
                    case 0:
                        delay = 73.44 / 1e6;
                        break;

                    default:
                        fail();
                        return;
                }

                assertEquals(queueDelay.getDelay(), delay);
            }
        }
    }

    @Test
    public void LokoPaperSiloForBananaPiTest() {
        /*
         * This test corresponds to the motivation section of the Loko NSDI paper.
         *
         * We just check that resources are allocated as computed in the papeR.
         */
        DetServConfig modelConfig = new DetServConfig(
                ACModel.TBM,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                false,
                new LowerLimit(new UpperLimit(
                        new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                        4), 1),
                (controller1, scheduler) -> new TBMSiloDefaultAllocation(controller1));

        // Systems
        new DNMSystem(controller);
        new DetServProxy(controller);

        // Create network
        Network myNetwork = networkingSystem.createNetwork();
        modelingConfigMapper.attachComponent(myNetwork.getQueueGraph(), modelConfig);
        // 100 Mbps and queue size of Zodiac for one port and 306 packet size
        NetworkTopology networkTopology = new GridRandom(networkingSystem, myNetwork, 6, 6, 125000000, 0, new double[]{3*306});

        for(Edge link : networkTopology.getNetwork().getLinkGraph().getEdges()) {
            Scheduler scheduler = schedulerMapper.get(link.getEntity());
            Queue[] queues = scheduler.getQueues();
            for(int i = 0; i < queues.length; i++) {
                Delay queueDelay = delayMapper.get(queues[i].getEntity());

                double delay;
                switch(i) {
                    case 0:
                        delay = 7.344 / 1e6;
                        break;

                    default:
                        fail();
                        return;
                }

                assertEquals(queueDelay.getDelay(), delay);
            }
        }
    }

    @Test
    public void MHMBurstIncreaseTest() {
        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm aStarPruneAlgorithm = new AStarPruneAlgorithm(controller, true); // necessary because now Minf metrics
        aStarPruneAlgorithm.setProxy(proxy);

        for(BurstIncreaseModel burstIncrease : BurstIncreaseModel.values()) {
            DetServConfig modelConfig = new DetServConfig(
                    ACModel.MHM,
                    ResidualMode.LEAST_LATENCY,
                    burstIncrease,
                    false,
                    new LowerLimit(new UpperLimit(
                            new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                            1), 0),
                    (controller1, scheduler) -> new MHMRateRatiosAllocation(controller1, new double[]{1.0 / 2, 1.0 / 4, 1.0 / 8}));

            modelConfig.initCostModel(controller);

            // Create network
            Network network = networkingSystem.createNetwork();
            modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

            NetworkNode h1 = networkingSystem.createNode(network);
            NetworkNode h2 = networkingSystem.createNode(network);
            NetworkNode h3 = networkingSystem.createNode(network);
            Link link1 = networkingSystem.createLinkWithPriorityScheduling(h1, h2, 1000000000.0 / 8, 0.0, new double[]{300000, 300000, 300000});
            Link link2 = networkingSystem.createLinkWithPriorityScheduling(h2, h3, 1000000000.0 / 8, 0.0, new double[]{300000, 300000, 300000});

            // Checking resource allocation (and getting the burst values)
            double[] acceptableBursts = new double[3];
            double[] maxDelays = new double[3];
            for(Edge linkEdges : network.getLinkGraph().getEdges()) {
                Scheduler scheduler = schedulerMapper.get(linkEdges.getEntity());
                Queue[] queues = scheduler.getQueues();
                for(int i = 0; i < queues.length; i++) {
                    Delay queueDelay = delayMapper.get(queues[i].getEntity());
                    MHMQueueModel queueModel = MHMQueueModelMapper.get(queues[i].getEntity());
                    acceptableBursts[i] = queueModel.getMaximumTokenBucket().getBurst().doubleValue();
                    maxDelays[i] = queueDelay.getDelay();

                    double rate;
                    switch(i) {
                        case 0:
                            rate = 1000000000.0 / 8 / 2;
                            break;

                        case 1:
                            rate = 1000000000.0 / 8 / 4;
                            break;

                        case 2:
                            rate = 1000000000.0 / 8 / 8;
                            break;

                        default:
                            fail();
                            return;
                    }

                    assertEquals(rate, queueModel.getMaximumTokenBucket().getUltAffineRate().doubleValue());
                }
            }

            // Checking access control and (de)-registration

            // Adding a flow supposed to be refused
            double deadline, flowBurst, flowRate;
            switch(burstIncrease) {
                case NO:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowBurst = acceptableBursts[0] * 1.01;
                    flowRate = 1000000000.0 / 8 / 2 * 0.999;
                    break;
                case WORST_CASE_BURST:
                case WORST_CASE_BURST_REAL_RESERVATION:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowBurst = 1000;
                    flowRate = (acceptableBursts[0] - flowBurst)/deadline * 1.01; // too high rate because burst will be increased too much
                    break;
                case REAL:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowBurst = 1000;
                    flowRate = (acceptableBursts[0] - flowBurst)/delayMapper.get(link1.getQueueEdges()[0].getEntity()).getDelay() * 1.01; // too high rate because burst will be increased too much
                    break;
                default:
                    throw new DNMException("Unknown burst increase type!");
            }

            UnicastRequest firstRefused = new UnicastRequest(h1.getQueueNode(), h3.getQueueNode());
            ArrivalCurve tb = CurvePwAffine.getFactory().createTokenBucket(flowRate, flowBurst);
            NCRequestData ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            Entity entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, firstRefused);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertFalse(pathMapper.isIn(entity));

            // Supposed to be accepted
            switch(burstIncrease) {
                case NO:
                    // Just close to burst and rate, supposed to be accepted because no burst increase
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowBurst = acceptableBursts[0] * 0.99;
                    flowRate = 1000000000.0 / 8 / 2 * 0.999;
                    break;
                case WORST_CASE_BURST:
                case WORST_CASE_BURST_REAL_RESERVATION:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowBurst = 1000;
                    flowRate = (acceptableBursts[0] - flowBurst)/deadline; // just fine rate so that burst is increased ok
                    break;
                case REAL:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowBurst = 1000;
                    flowRate = Math.min(1000000000.0 / 8 / 2, (acceptableBursts[0] - flowBurst)/delayMapper.get(link1.getQueueEdges()[0].getEntity()).getDelay()); // just fine rate so that burst is increased ok
                    break;
                default:
                    throw new DNMException("Unknown burst increase type!");
            }
            UnicastRequest firstAccepted = new UnicastRequest(h1.getQueueNode(), h3.getQueueNode());
            tb = CurvePwAffine.getFactory().createTokenBucket( flowRate, flowBurst);
            ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, firstAccepted);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertTrue(pathMapper.isIn(entity)); // should be accepted
            assertEquals(2, pathMapper.get(entity).getPath().length);
            assertEquals(link1.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]); // should be high priority edge
            assertEquals(link2.getQueueEdges()[0], pathMapper.get(entity).getPath()[1]); // should be high priority edge

            // Checking resource reservation
            double[] burstReservations, rateReservations;
            rateReservations = new double[]{flowRate, flowRate};
            switch(burstIncrease) {
                case NO:
                    burstReservations = new double[]{flowBurst, flowBurst};
                    break;
                case WORST_CASE_BURST:
                    burstReservations = new double[]{flowBurst, flowBurst + flowRate * deadline};
                    break;
                case WORST_CASE_BURST_REAL_RESERVATION:
                case REAL:
                    burstReservations = new double[]{flowBurst, flowBurst + flowRate * delayMapper.get(link1.getQueueEdges()[0].getEntity()).getDelay()};
                    break;
                default:
                    throw new DNMException("Unknown burst increase type!");
            }

            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[0], burstReservations[0]), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[2].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[1], burstReservations[1]), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[2].getEntity()).getTokenBucket());

            // Checking deregistration
            requestMapper.detachComponent(firstRefused);
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[0], burstReservations[0]), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[2].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[1], burstReservations[1]), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[2].getEntity()).getTokenBucket());
            requestMapper.detachComponent(firstAccepted);
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[2].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[2].getEntity()).getTokenBucket());
        }
    }

    @Test
    public void TBMBurstIncreaseTest() {
        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm aStarPruneAlgorithm = new AStarPruneAlgorithm(controller, true); // necessary because now Minf metrics
        aStarPruneAlgorithm.setProxy(proxy);

        double[] maxDelays = new double[]{1.74/1000, 6.6/1000, 11.22/1000};
        for(BurstIncreaseModel burstIncrease : BurstIncreaseModel.values()) {
            DetServConfig modelConfig = new DetServConfig(
                    ACModel.TBM,
                    ResidualMode.LEAST_LATENCY,
                    burstIncrease,
                    false,
                    new LowerLimit(new UpperLimit(
                            new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                            1), 0),
                    (controller1, scheduler) -> new TBMStaticDelaysAllocation(controller1, maxDelays));

            modelConfig.initCostModel(controller);

            // Create network
            Network network = networkingSystem.createNetwork();
            modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

            NetworkNode h1 = networkingSystem.createNode(network);
            NetworkNode h2 = networkingSystem.createNode(network);
            NetworkNode h3 = networkingSystem.createNode(network);
            Link link1 = networkingSystem.createLinkWithPriorityScheduling(h1, h2, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});
            Link link2 = networkingSystem.createLinkWithPriorityScheduling(h2, h3, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

            // Checking access control and (de)-registration

            // Adding a flow supposed to be refused
            double deadline, flowBurst, flowRate;
            switch(burstIncrease) {
                case NO:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowRate = 500000000.0 / 8;  // link rate
                    flowBurst = ((1000000000.0/8) * maxDelays[0] - modelConfig.getMaximumPacketSize()) * 1.01;
                    break;
                case WORST_CASE_BURST:
                case WORST_CASE_BURST_REAL_RESERVATION:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowRate = 100000000.0 / 8;  // link rate
                    flowBurst = (1000000000.0/8*maxDelays[0] - flowRate*deadline - modelConfig.getMaximumPacketSize()) * 1.01; // include burst increase
                    break;
                case REAL:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowRate = 100000000.0 / 8;  // link rate
                    flowBurst = ((1000000000.0/8 - flowRate)*maxDelays[0] - modelConfig.getMaximumPacketSize()) * 1.01; // include burst increase
                    break;
                default:
                    throw new DNMException("Unknown burst increase type!");
            }

            UnicastRequest firstRefused = new UnicastRequest(h1.getQueueNode(), h3.getQueueNode());
            ArrivalCurve tb = CurvePwAffine.getFactory().createTokenBucket( flowRate, flowBurst);
            NCRequestData ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            Entity entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, firstRefused);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertFalse(pathMapper.isIn(entity));

            // Supposed to be accepted
            switch(burstIncrease) {
                case NO:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowRate = 500000000.0 / 8;  // link rate
                    flowBurst = ((1000000000.0/8) * maxDelays[0] - modelConfig.getMaximumPacketSize()) * 0.99;
                    break;
                case WORST_CASE_BURST:
                case WORST_CASE_BURST_REAL_RESERVATION:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowRate = 100000000.0 / 8;  // link rate
                    flowBurst = (1000000000.0/8*maxDelays[0] - flowRate*deadline - modelConfig.getMaximumPacketSize()) * 0.99; // include burst increase
                    break;
                case REAL:
                    deadline = maxDelays[0] * 2 * 1.0001; // so that it goes to the high priority queues
                    flowRate = 100000000.0 / 8;  // link rate
                    flowBurst = ((1000000000.0/8 - flowRate)*maxDelays[0] - modelConfig.getMaximumPacketSize()) * 0.99; // include burst increase
                    break;
                default:
                    throw new DNMException("Unknown burst increase type!");
            }
            UnicastRequest firstAccepted = new UnicastRequest(h1.getQueueNode(), h3.getQueueNode());
            tb = CurvePwAffine.getFactory().createTokenBucket( flowRate, flowBurst);
            ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, firstAccepted);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertTrue(pathMapper.isIn(entity)); // should be accepted
            assertEquals(2, pathMapper.get(entity).getPath().length);
            assertEquals(link1.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]); // should be high priority edge
            assertEquals(link2.getQueueEdges()[0], pathMapper.get(entity).getPath()[1]); // should be high priority edge

            // Checking resource reservation
            double[] burstReservations, rateReservations;
            rateReservations = new double[]{flowRate, flowRate};
            switch(burstIncrease) {
                case NO:
                    burstReservations = new double[]{flowBurst, flowBurst};
                    break;
                case WORST_CASE_BURST:
                    burstReservations = new double[]{flowBurst, flowBurst + flowRate * deadline};
                    break;
                case REAL:
                case WORST_CASE_BURST_REAL_RESERVATION:
                    burstReservations = new double[]{flowBurst, flowBurst + flowRate * delayMapper.get(link1.getQueueEdges()[0].getEntity()).getDelay()};
                    break;
                default:
                    throw new DNMException("Unknown burst increase type!");
            }

            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[0], burstReservations[0]), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[2].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[1], burstReservations[1]), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[2].getEntity()).getTokenBucket());

            // Checking deregistration
            requestMapper.detachComponent(firstRefused);
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[0], burstReservations[0]), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[2].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(rateReservations[1], burstReservations[1]), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[2].getEntity()).getTokenBucket());
            requestMapper.detachComponent(firstAccepted);
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link1.getQueueEdges()[2].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[0].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[1].getEntity()).getTokenBucket());
            assertEquals(CurvePwAffine.getFactory().createTokenBucket(0, 0), tokenBucketUtilizationMapper.get(link2.getQueueEdges()[2].getEntity()).getTokenBucket());
        }
    }

    @Test
    public void ILSforTBMTest() {
        double linkRate = 1000000000.0 / 8;

        // This test actually does not check residual config.
        //
        // Indeed: least latency and highest slope residual config are the same here. The shaped arrival curve
        // slope is link rate, and the service curve is lower or equal to this, hence, the intersection of service
        // curve and shaped arrival curve always happens in the last segment of the shaped arrival curve and
        // hence the residual service curve is always a rate-latency curve. Hence, even real residual mode is the same.

        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm aStarPruneAlgorithm = new AStarPruneAlgorithm(controller, true);
        aStarPruneAlgorithm.setProxy(proxy);

        for(ResidualMode residualMode : ResidualMode.values()) {
            DetServConfig modelConfig = new DetServConfig(
                    ACModel.TBM,
                    residualMode,
                    BurstIncreaseModel.NO,
                    true,
                    new LowerLimit(new UpperLimit(
                            new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                            1), 0),
                    (controller1, scheduler) -> new TBMStaticDelaysAllocation(controller1, new double[]{3.0 / 1000, 10.0 / 1000}));
            modelConfig.initCostModel(controller);
            double T = 2 * modelConfig.getMaximumPacketSize()/linkRate;

            // Create network
            Network network = networkingSystem.createNetwork();
            modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

            // We create a star network of 5 hosts:
            //              kovacic
            //                 |
            // hazard ----- chelsea ------ willian
            //                 |
            //              barkley
            //
            // hazard, kovacic, barkley will send towards willian.
            // We will check that there is indeed correct shaping on chelsea-willian but not on the *-chelsea links
            // (as these are the source links of the flows and hence shouldn't be shaped)
            //
            // Using the delay constraint, we'll force all the flows to go to high priority queue, and we'll check if
            // the service curve of the lower priority queue is correct based on the residual mode.
            //
            Host hazard = networkingSystem.createHost(network, "hazard");
            Host kovacic = networkingSystem.createHost(network, "kovacic");
            Host barkley = networkingSystem.createHost(network, "barkley");
            Host willian = networkingSystem.createHost(network, "willian");
            NetworkNode chelsea = networkingSystem.createNode(network, "chelsea");

            NetworkNode hazardNode = networkingSystem.addInterface(hazard, new NetworkInterface("eth0", "00:00:00:00:00:01", "125.2.2.1"));
            NetworkNode kovacicNode = networkingSystem.addInterface(kovacic, new NetworkInterface("eth0", "00:00:00:00:00:02", "125.2.2.2"));
            NetworkNode barkleyNode = networkingSystem.addInterface(barkley, new NetworkInterface("eth0", "00:00:00:00:00:03", "125.2.2.3"));
            NetworkNode willianNode = networkingSystem.addInterface(willian, new NetworkInterface("eth0", "00:00:00:00:00:04", "125.2.2.4"));

            // Links: we just use one priority queue to simplify
            Link hazardChelsea = networkingSystem.createLinkWithPriorityScheduling(hazardNode, chelsea, linkRate, 0.0, new double[]{300000, 300000});
            Link chelseaHazard = networkingSystem.createLinkWithPriorityScheduling(chelsea, hazardNode, linkRate, 0.0, new double[]{300000, 300000});

            Link kovacicChelsea = networkingSystem.createLinkWithPriorityScheduling(kovacicNode, chelsea, linkRate, 0.0, new double[]{300000, 300000});
            Link chelseaKovacic = networkingSystem.createLinkWithPriorityScheduling(chelsea, kovacicNode, linkRate, 0.0, new double[]{300000, 300000});

            Link willianChelsea = networkingSystem.createLinkWithPriorityScheduling(willianNode, chelsea, linkRate, 0.0, new double[]{300000, 300000});
            Link chelseaWillian = networkingSystem.createLinkWithPriorityScheduling(chelsea, willianNode, linkRate, 0.0, new double[]{300000, 300000});

            Link barkleyChelsea = networkingSystem.createLinkWithPriorityScheduling(barkleyNode, chelsea, linkRate, 0.0, new double[]{300000, 300000});
            Link chelseaBarkley = networkingSystem.createLinkWithPriorityScheduling(chelsea, barkleyNode, linkRate, 0.0, new double[]{300000, 300000});

            // First we add a flow which should be rejected.
            // Reason is that there was a bug: if incoming was not there yet in data structure, access was always
            // granted. Access was only checked when a flow is already there. So we add a huge flow (which should be
            // rejected) when nothing is embedded yet to check this bug. We just use one hop to make sure the second
            // hop is not the reason for rejection.

            // Impossible flow
            UnicastRequest impossibleFlow = new UnicastRequest(hazardNode.getQueueNode(), chelsea.getQueueNode());
            double deadline = 8.0 / 1000; // so that low priority cannot
            ArrivalCurve tb = CurvePwAffine.getFactory().createTokenBucket(linkRate * 4, 110000);
            NCRequestData ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            Entity entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, impossibleFlow);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertFalse(pathMapper.isIn(entity)); // should be rejected

            // Checking resource reservation
            // To simplify, we use an array of pairs: <Queue, Map<Edge, TokenBucket>>
            // That is, for each queue, we check, for each incoming edge, if the correct token bucket is reserved
            LinkedList<org.javatuples.Pair<Edge, Map<Edge, ArrivalCurve>>> expectedReservations = new LinkedList<>();

            // Everything is empty
            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[1], new HashMap<>()));


            for (org.javatuples.Pair<Edge, Map<Edge, ArrivalCurve>> element : expectedReservations) {
                Edge testEdge = element.getValue0();
                Map<Edge, ArrivalCurve> testEdgeExpectedReservationMap = element.getValue1();
                Set<Map.Entry<Edge, Pair<Num>>> testEdgeReservationMap = perInEdgeTokenBucketUtilizationMapper.get(testEdge.getEntity()).getTokenBuckets();
                assertEquals(testEdgeExpectedReservationMap.size(), testEdgeReservationMap.size());
                // If size are same, let's check the entries
                for (Map.Entry<Edge, Pair<Num>> reservationEntry : testEdgeReservationMap) {
                    Edge inEdge = reservationEntry.getKey();
                    ArrivalCurve reservedTb = CurvePwAffine.getFactory().createTokenBucket(reservationEntry.getValue().getFirst(), reservationEntry.getValue().getSecond());
                    assertTrue(testEdgeExpectedReservationMap.containsKey(inEdge));
                    ArrivalCurve expectedTb = testEdgeExpectedReservationMap.get(inEdge);
                    assertEquals(expectedTb, reservedTb);
                }
            }

            // Checking residual curves: should all be full link
            Map<Edge, ServiceCurve> expectedServiceCurves = new HashMap<>();
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));

            for(Map.Entry<Edge, ServiceCurve> expectedEntry : expectedServiceCurves.entrySet()) {
                Edge queueEdge = expectedEntry.getKey();
                ServiceCurve expectedServiceCurve = expectedEntry.getValue();
                ServiceCurve realServiceCurve = queueModelMapper.get(queueEdge.getEntity()).getServiceCurve();
                // We should here always have rate latency curves:
                ((ServiceCurve_DNC) realServiceCurve).makeRateLatency();
                assertEquals(expectedServiceCurve, realServiceCurve);
            }

            // First from hazard to willian
            UnicastRequest hazardFlow = new UnicastRequest(hazardNode.getQueueNode(), willianNode.getQueueNode());
            deadline = 8.0 / 1000; // so that low priority cannot
            // We'll add three flows, so total should be less than line rate
            // hazard will send the most (:D): 1/3, barkley 1/4 and kovacic 1/5 (sorry you've let France win...)
            // we also send more than the queue burst capacity in total (each 110000) to show the benefit of shaping
            // (as without shaping, flows wouldn't be accepted since they have a total burst higher than the queue capacity)
            tb = CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000);
            ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, hazardFlow);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertTrue(pathMapper.isIn(entity)); // should be accepted
            assertEquals(2, pathMapper.get(entity).getPath().length);
            assertEquals(hazardChelsea.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]);
            assertEquals(chelseaWillian.getQueueEdges()[0], pathMapper.get(entity).getPath()[1]); // should be high priority edge

            // Checking resource reservation
            expectedReservations = new LinkedList<>();

            // Reservation from hazard to chelsea...
            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(hazardChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000));
            }}));
            // ... and chelsea to hazard!
            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(hazardChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000));
            }}));
            // Rest is empty
            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[1], new HashMap<>()));

            for (org.javatuples.Pair<Edge, Map<Edge, ArrivalCurve>> element : expectedReservations) {
                Edge testEdge = element.getValue0();
                Map<Edge, ArrivalCurve> testEdgeExpectedReservationMap = element.getValue1();
                Set<Map.Entry<Edge, Pair<Num>>> testEdgeReservationMap = perInEdgeTokenBucketUtilizationMapper.get(testEdge.getEntity()).getTokenBuckets();
                assertEquals(testEdgeExpectedReservationMap.size(), testEdgeReservationMap.size());
                // If size are same, let's check the entries
                for (Map.Entry<Edge, Pair<Num>> reservationEntry : testEdgeReservationMap) {
                    Edge inEdge = reservationEntry.getKey();
                    ArrivalCurve reservedTb = CurvePwAffine.getFactory().createTokenBucket(reservationEntry.getValue().getFirst(), reservationEntry.getValue().getSecond());
                    assertTrue(testEdgeExpectedReservationMap.containsKey(inEdge));
                    ArrivalCurve expectedTb = testEdgeExpectedReservationMap.get(inEdge);
                    assertEquals(expectedTb, reservedTb);
                }
            }

            // Checking residual curves, which should be based on the input-link shaped arrival curves.
            expectedServiceCurves = new HashMap<>();
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            // new service curve: no shaping so: new t is (b + RT)/(R-r)
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(
                    Num.getUtils().sub(
                            Num.getFactory().create(linkRate),
                            Num.getFactory().create(linkRate / 3)),
                    Num.getUtils().div(
                            Num.getUtils().add(Num.getFactory().create(110000), Num.getUtils().mult(Num.getFactory().create(T), Num.getFactory().create(linkRate))),
                            Num.getUtils().sub(Num.getFactory().create(linkRate), Num.getFactory().create(linkRate/3)))
            ));
            // Shaping happened here, curve should be least-latency of RL(R,T) - shaped arrival curve
            ArrivalCurve shapingCurve = CurvePwAffine.getFactory().createTokenBucket(linkRate, modelConfig.getMaximumPacketSize());
            ArrivalCurve originalCurve = CurvePwAffine.getFactory().createTokenBucket(linkRate/3, 110000);
            // T is just the min of interesections of service curve with both token buckets
            ServiceCurve upperServiceCurve = CurvePwAffine.getFactory().createRateLatency(linkRate, T);
            Num expectedT = Num.getUtils().min(CurvePwAffine.getXIntersection(shapingCurve, upperServiceCurve), CurvePwAffine.getXIntersection(originalCurve, upperServiceCurve));
            Num expectedR = Num.getFactory().create(linkRate - linkRate / 3);
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(expectedR, expectedT));

            for(Map.Entry<Edge, ServiceCurve> expectedEntry : expectedServiceCurves.entrySet()) {
                Edge queueEdge = expectedEntry.getKey();
                ServiceCurve expectedServiceCurve = expectedEntry.getValue();
                ServiceCurve realServiceCurve = queueModelMapper.get(queueEdge.getEntity()).getServiceCurve();
                // We should here always have rate latency curves:
                ((ServiceCurve_DNC) realServiceCurve).makeRateLatency();
                assertEquals(expectedServiceCurve, realServiceCurve);
            }

            // Second flow from barkley to willian
            UnicastRequest barkleyFlow = new UnicastRequest(barkleyNode.getQueueNode(), willianNode.getQueueNode());
            deadline = 8.0 / 1000; // so that low priority cannot
            // barkley 1/4
            tb = CurvePwAffine.getFactory().createTokenBucket(linkRate / 4, 110000);
            ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, barkleyFlow);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertTrue(pathMapper.isIn(entity)); // should be accepted
            assertEquals(2, pathMapper.get(entity).getPath().length);
            assertEquals(barkleyChelsea.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]);
            assertEquals(chelseaWillian.getQueueEdges()[0], pathMapper.get(entity).getPath()[1]); // should be high priority edge

            // Checking resource reservation
            expectedReservations = new LinkedList<>();

            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(barkleyChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 4, 110000));
            }}));

            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(hazardChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000));
            }}));

            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(hazardChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000));
                put(barkleyChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 4, 110000));
            }}));

            // Rest is empty
            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[1], new HashMap<>()));

            for (org.javatuples.Pair<Edge, Map<Edge, ArrivalCurve>> element : expectedReservations) {
                Edge testEdge = element.getValue0();
                Map<Edge, ArrivalCurve> testEdgeExpectedReservationMap = element.getValue1();
                Set<Map.Entry<Edge, Pair<Num>>> testEdgeReservationMap = perInEdgeTokenBucketUtilizationMapper.get(testEdge.getEntity()).getTokenBuckets();
                assertEquals(testEdgeExpectedReservationMap.size(), testEdgeReservationMap.size());
                // If size are same, let's check the entries
                for (Map.Entry<Edge, Pair<Num>> reservationEntry : testEdgeReservationMap) {
                    Edge inEdge = reservationEntry.getKey();
                    ArrivalCurve reservedTb = CurvePwAffine.getFactory().createTokenBucket(reservationEntry.getValue().getFirst(), reservationEntry.getValue().getSecond());
                    assertTrue(testEdgeExpectedReservationMap.containsKey(inEdge));
                    ArrivalCurve expectedTb = testEdgeExpectedReservationMap.get(inEdge);
                    assertEquals(expectedTb, reservedTb);
                }
            }

            // Checking residual curves, which should be based on the input-link shaped arrival curves.
            expectedServiceCurves = new HashMap<>();
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            // new service curve: no shaping so: new t is (b + RT)/(R-r)
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(
                    Num.getUtils().sub(
                            Num.getFactory().create(linkRate),
                            Num.getFactory().create(linkRate / 4)),
                    Num.getUtils().div(
                            Num.getUtils().add(Num.getFactory().create(110000), Num.getUtils().mult(Num.getFactory().create(T), Num.getFactory().create(linkRate))),
                            Num.getUtils().sub(Num.getFactory().create(linkRate), Num.getFactory().create(linkRate/4)))
            ));
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            // new service curve: no shaping so: new t is (b + RT)/(R-r)
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(
                    Num.getUtils().sub(
                            Num.getFactory().create(linkRate),
                            Num.getFactory().create(linkRate / 3)),
                    Num.getUtils().div(
                            Num.getUtils().add(Num.getFactory().create(110000), Num.getUtils().mult(Num.getFactory().create(T), Num.getFactory().create(linkRate))),
                            Num.getUtils().sub(Num.getFactory().create(linkRate), Num.getFactory().create(linkRate/3)))
            ));
            // Shaping happened here, curve should be least-latency of RL(R,T) - shaped arrival curve
            ArrivalCurve shapedCurve = CurvePwAffine.getFactory().createTokenBucket(linkRate/3, 110000);
            shapedCurve = CurvePwAffine.min(shapedCurve, CurvePwAffine.getFactory().createTokenBucket(linkRate, modelConfig.getMaximumPacketSize()));
            shapedCurve = CurvePwAffine.add(shapedCurve,
                    CurvePwAffine.min(
                            CurvePwAffine.getFactory().createTokenBucket(linkRate/4, 110000),
                            CurvePwAffine.getFactory().createTokenBucket(linkRate, modelConfig.getMaximumPacketSize())
                    ));
            // T is just the intersection of service curve with the aggregated arrival curve
            upperServiceCurve = CurvePwAffine.getFactory().createRateLatency(linkRate, T);
            expectedT = CurvePwAffine.getXIntersection(shapedCurve, upperServiceCurve);
            expectedR = Num.getFactory().create(linkRate - linkRate / 3 - linkRate / 4);
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(expectedR, expectedT));

            for(Map.Entry<Edge, ServiceCurve> expectedEntry : expectedServiceCurves.entrySet()) {
                Edge queueEdge = expectedEntry.getKey();
                ServiceCurve expectedServiceCurve = expectedEntry.getValue();
                ServiceCurve realServiceCurve = queueModelMapper.get(queueEdge.getEntity()).getServiceCurve();
                // We should here always have rate latency curves:
                ((ServiceCurve_DNC) realServiceCurve).makeRateLatency();
                assertEquals(expectedServiceCurve, realServiceCurve);
            }

            // Third flow from kovacic to willian
            UnicastRequest kovacicFlow = new UnicastRequest(kovacicNode.getQueueNode(), willianNode.getQueueNode());
            deadline = 8.0 / 1000; // so that low priority cannot
            // barkley 1/4
            tb = CurvePwAffine.getFactory().createTokenBucket(linkRate / 5, 110000);
            ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

            entity = controller.createEntity();
            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                requestMapper.attachComponent(entity, kovacicFlow);
                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(aStarPruneAlgorithm));
            }

            assertTrue(pathMapper.isIn(entity)); // should be accepted
            assertEquals(2, pathMapper.get(entity).getPath().length);
            assertEquals(kovacicChelsea.getQueueEdges()[0], pathMapper.get(entity).getPath()[0]);
            assertEquals(chelseaWillian.getQueueEdges()[0], pathMapper.get(entity).getPath()[1]); // should be high priority edge

            // Checking resource reservation
            expectedReservations = new LinkedList<>();

            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(kovacicChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 5, 110000));
            }}));

            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(barkleyChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 4, 110000));
            }}));

            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(hazardChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000));
            }}));

            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[0], new HashMap<Edge, ArrivalCurve>() {{
                put(hazardChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 3, 110000));
                put(barkleyChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 4, 110000));
                put(kovacicChelsea.getLinkEdge(), CurvePwAffine.getFactory().createTokenBucket(linkRate / 5, 110000));
            }}));

            // Rest is empty
            expectedReservations.add(new org.javatuples.Pair<>(chelseaWillian.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaBarkley.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaHazard.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[0], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(chelseaKovacic.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(willianChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(kovacicChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(barkleyChelsea.getQueueEdges()[1], new HashMap<>()));
            expectedReservations.add(new org.javatuples.Pair<>(hazardChelsea.getQueueEdges()[1], new HashMap<>()));

            for (org.javatuples.Pair<Edge, Map<Edge, ArrivalCurve>> element : expectedReservations) {
                Edge testEdge = element.getValue0();
                Map<Edge, ArrivalCurve> testEdgeExpectedReservationMap = element.getValue1();
                Set<Map.Entry<Edge, Pair<Num>>> testEdgeReservationMap = perInEdgeTokenBucketUtilizationMapper.get(testEdge.getEntity()).getTokenBuckets();
                assertEquals(testEdgeExpectedReservationMap.size(), testEdgeReservationMap.size());
                // If size are same, let's check the entries
                for (Map.Entry<Edge, Pair<Num>> reservationEntry : testEdgeReservationMap) {
                    Edge inEdge = reservationEntry.getKey();
                    ArrivalCurve reservedTb = CurvePwAffine.getFactory().createTokenBucket(reservationEntry.getValue().getFirst(), reservationEntry.getValue().getSecond());
                    assertTrue(testEdgeExpectedReservationMap.containsKey(inEdge));
                    ArrivalCurve expectedTb = testEdgeExpectedReservationMap.get(inEdge);
                    assertEquals(expectedTb, reservedTb);
                }
            }

            // Checking residual curves, which should be based on the input-link shaped arrival curves.
            expectedServiceCurves = new HashMap<>();
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaKovacic.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            // new service curve: no shaping so: new t is (b + RT)/(R-r)
            expectedServiceCurves.put(kovacicChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(
                    Num.getUtils().sub(
                            Num.getFactory().create(linkRate),
                            Num.getFactory().create(linkRate / 5)),
                    Num.getUtils().div(
                            Num.getUtils().add(Num.getFactory().create(110000), Num.getUtils().mult(Num.getFactory().create(T), Num.getFactory().create(linkRate))),
                            Num.getUtils().sub(Num.getFactory().create(linkRate), Num.getFactory().create(linkRate/5)))
            ));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaHazard.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(chelseaBarkley.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            // new service curve: no shaping so: new t is (b + RT)/(R-r)
            expectedServiceCurves.put(barkleyChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(
                    Num.getUtils().sub(
                            Num.getFactory().create(linkRate),
                            Num.getFactory().create(linkRate / 4)),
                    Num.getUtils().div(
                            Num.getUtils().add(Num.getFactory().create(110000), Num.getUtils().mult(Num.getFactory().create(T), Num.getFactory().create(linkRate))),
                            Num.getUtils().sub(Num.getFactory().create(linkRate), Num.getFactory().create(linkRate/4)))
            ));
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[0], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            expectedServiceCurves.put(willianChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(linkRate, T));
            // new service curve: no shaping so: new t is (b + RT)/(R-r)
            expectedServiceCurves.put(hazardChelsea.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(
                    Num.getUtils().sub(
                            Num.getFactory().create(linkRate),
                            Num.getFactory().create(linkRate / 3)),
                    Num.getUtils().div(
                            Num.getUtils().add(Num.getFactory().create(110000), Num.getUtils().mult(Num.getFactory().create(T), Num.getFactory().create(linkRate))),
                            Num.getUtils().sub(Num.getFactory().create(linkRate), Num.getFactory().create(linkRate/3)))
            ));
            // Shaping happened here, curve should be least-latency of RL(R,T) - shaped arrival curve
            shapedCurve = CurvePwAffine.getFactory().createTokenBucket(linkRate/3, 110000);
            shapedCurve = CurvePwAffine.min(shapedCurve, CurvePwAffine.getFactory().createTokenBucket(linkRate, modelConfig.getMaximumPacketSize()));
            shapedCurve = CurvePwAffine.add(shapedCurve,
                    CurvePwAffine.min(
                            CurvePwAffine.getFactory().createTokenBucket(linkRate/4, 110000),
                            CurvePwAffine.getFactory().createTokenBucket(linkRate, modelConfig.getMaximumPacketSize())
                    ));
            shapedCurve = CurvePwAffine.add(shapedCurve,
                    CurvePwAffine.min(
                            CurvePwAffine.getFactory().createTokenBucket(linkRate/5, 110000),
                            CurvePwAffine.getFactory().createTokenBucket(linkRate, modelConfig.getMaximumPacketSize())
                    ));
            // T is just the intersection of service curve with the aggregated arrival curve
            upperServiceCurve = CurvePwAffine.getFactory().createRateLatency(linkRate, T);
            expectedT = CurvePwAffine.getXIntersection(shapedCurve, upperServiceCurve);
            expectedR = Num.getFactory().create(linkRate - linkRate / 3 - linkRate / 4 - linkRate / 5);
            expectedServiceCurves.put(chelseaWillian.getQueueEdges()[1], CurvePwAffine.getFactory().createRateLatency(expectedR, expectedT));

            for(Map.Entry<Edge, ServiceCurve> expectedEntry : expectedServiceCurves.entrySet()) {
                Edge queueEdge = expectedEntry.getKey();
                ServiceCurve expectedServiceCurve = expectedEntry.getValue();
                ServiceCurve realServiceCurve = queueModelMapper.get(queueEdge.getEntity()).getServiceCurve();
                // We should here always have rate latency curves:
                ((ServiceCurve_DNC) realServiceCurve).makeRateLatency();
                assertEquals(expectedServiceCurve, realServiceCurve);
            }
        }
    }

    @Test
    public void ILSforMHMTest() {
        DetServConfig modelConfig = new DetServConfig(
                ACModel.MHM,
                ResidualMode.LEAST_LATENCY,
                BurstIncreaseModel.NO,
                true,
                new LowerLimit(new UpperLimit(
                        new Division(new Constant(), new Summation(new Constant(), new QueuePriority())),
                        1), 0),
                (controller1, scheduler) -> new MHMRateRatiosAllocation(controller1, new double[]{1.0/2, 1.0/4, 1.0/8}));

        // DNC
        new DNMSystem(controller);
        DetServProxy proxy = new DetServProxy(controller);

        // Routing
        new RoutingSystem(controller);
        RoutingAlgorithm aStarPruneAlgorithm = new AStarPruneAlgorithm(controller, true);
        aStarPruneAlgorithm.setProxy(proxy);
        modelConfig.initCostModel(controller);

        // Create network
        Network network = networkingSystem.createNetwork();
        modelingConfigMapper.attachComponent(network.getQueueGraph(), modelConfig);

        // Create a network with hosts (no ILS), and different nb of input links (2 to liverpool, 3 for chelsea)

        Host hazard = networkingSystem.createHost(network, "hazard");
        Host kane = networkingSystem.createHost(network, "kane");
        Host gerrard = networkingSystem.createHost(network, "gerrard");

        NetworkNode liverpool = networkingSystem.createNode(network, "liverpool");
        NetworkNode chelsea = networkingSystem.createNode(network, "chelsea");

        NetworkNode hazardNode = networkingSystem.addInterface(hazard, new NetworkInterface("eth0", "00:00:00:00:00:01", "125.2.2.1"));
        NetworkNode kaneNode = networkingSystem.addInterface(kane, new NetworkInterface("eth0", "00:00:00:00:00:02", "125.2.2.2"));
        NetworkNode gerrardNode = networkingSystem.addInterface(gerrard, new NetworkInterface("eth0", "00:00:00:00:00:03", "125.2.2.3"));

        // inter switch
        Link interSwitch1 = networkingSystem.createLinkWithPriorityScheduling(chelsea, liverpool, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});
        Link interSwitch2 = networkingSystem.createLinkWithPriorityScheduling(liverpool, chelsea, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

        // hosts
        Link nodeToSwitch1 = networkingSystem.createLinkWithPriorityScheduling(hazardNode, chelsea, 1000000000.0/8, 0.0, new double[]{300000});
        Link switchToNode1 = networkingSystem.createLinkWithPriorityScheduling(chelsea, hazardNode, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

        Link nodeToSwitch2 = networkingSystem.createLinkWithPriorityScheduling(kaneNode, liverpool, 1000000000.0/8, 0.0, new double[]{300000});
        Link switchToNode2 = networkingSystem.createLinkWithPriorityScheduling(liverpool, kaneNode, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

        Link nodeToSwitch3 = networkingSystem.createLinkWithPriorityScheduling(gerrardNode, liverpool, 1000000000.0/8, 0.0, new double[]{300000});
        Link switchToNode3 = networkingSystem.createLinkWithPriorityScheduling(liverpool, gerrardNode, 1000000000.0/8, 0.0, new double[]{300000, 300000, 300000});

        Link[] linksThatShouldNotHaveAnyCorrection = new Link[]{nodeToSwitch1, nodeToSwitch2, nodeToSwitch3};
        for(Link link : linksThatShouldNotHaveAnyCorrection) {
            for(Edge edge : link.getQueueEdges()) {
                MHMQueueModel MHMModel = MHMQueueModelMapper.get(edge.getEntity());
                ArrivalCurve maxTb = MHMModel.getMaximumTokenBucket();
                ServiceCurve serviceCurve = MHMModel.getServiceCurve();
                Delay delay = delayMapper.get(edge.getEntity());
                // Delay should be not corrected (that is, simply the deviation TB / SC)
                assertEquals(Bound.delayFIFO(maxTb, serviceCurve).doubleValue(), delay.getDelay());
            }
        }

        Link[] linksWith2IL = new Link[]{switchToNode1, interSwitch1};
        for(Link link : linksWith2IL) {
            for(Edge edge : link.getQueueEdges()) {
                MHMQueueModel MHMModel = MHMQueueModelMapper.get(edge.getEntity());
                ArrivalCurve maxTb = MHMModel.getMaximumTokenBucket();
                ServiceCurve serviceCurve = MHMModel.getServiceCurve();
                Delay delay = delayMapper.get(edge.getEntity());
                // The delay should have been reduced
                assertTrue(Bound.delayFIFO(maxTb, serviceCurve).doubleValue() > delay.getDelay());

                // Shaping curve is actually from 2 IL
                ArrivalCurve shaping = CurvePwAffine.min(CurvePwAffine.getFactory().createTokenBucket(2 * 1000000000.0/8, 2 * modelConfig.getMaximumPacketSize()), maxTb);
                assertEquals(Bound.delayFIFO(shaping, serviceCurve).doubleValue(), delay.getDelay());
            }
        }

        Link[] linksWith3IL = new Link[]{switchToNode2, switchToNode3, interSwitch2};
        for(Link link : linksWith3IL) {
            for(Edge edge : link.getQueueEdges()) {
                MHMQueueModel MHMModel = MHMQueueModelMapper.get(edge.getEntity());
                ArrivalCurve maxTb = MHMModel.getMaximumTokenBucket();
                ServiceCurve serviceCurve = MHMModel.getServiceCurve();
                Delay delay = delayMapper.get(edge.getEntity());
                // The delay should have been reduced
                assertTrue(Bound.delayFIFO(maxTb, serviceCurve).doubleValue() > delay.getDelay());

                // Shaping curve is actually from 3 IL
                ArrivalCurve shaping = CurvePwAffine.min(CurvePwAffine.getFactory().createTokenBucket(3 * 1000000000.0/8, 3 * modelConfig.getMaximumPacketSize()), maxTb);
                assertEquals(Bound.delayFIFO(shaping, serviceCurve).doubleValue(), delay.getDelay());
            }
        }
    }
}

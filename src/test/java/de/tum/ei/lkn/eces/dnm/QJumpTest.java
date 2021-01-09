package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.MapperSpace;
import de.tum.ei.lkn.eces.dnm.config.QJumpConfig;
import de.tum.ei.lkn.eces.dnm.mappers.NCRequestDataMapper;
import de.tum.ei.lkn.eces.dnm.mappers.QJumpConfigMapper;
import de.tum.ei.lkn.eces.dnm.proxies.QJumpProxy;
import de.tum.ei.lkn.eces.graph.GraphSystem;
import de.tum.ei.lkn.eces.network.Network;
import de.tum.ei.lkn.eces.network.NetworkingSystem;
import de.tum.ei.lkn.eces.routing.RoutingSystem;
import de.tum.ei.lkn.eces.routing.SelectedRoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.RoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.sp.unicast.dijkstra.DijkstraAlgorithm;
import de.tum.ei.lkn.eces.routing.mappers.PathMapper;
import de.tum.ei.lkn.eces.routing.mappers.RequestMapper;
import de.tum.ei.lkn.eces.routing.mappers.SelectedRoutingAlgorithmMapper;
import de.tum.ei.lkn.eces.routing.requests.UnicastRequest;
import de.tum.ei.lkn.eces.topologies.NetworkTopology;
import de.tum.ei.lkn.eces.topologies.networktopologies.GridRandom;
import de.uni_kl.cs.discodnc.curves.ArrivalCurve;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Random;

import static junit.framework.TestCase.*;

public class QJumpTest {
    private Controller controller;
    private QJumpConfigMapper qjumpConfigMapper;
    private GraphSystem graphSystem;
    private NetworkingSystem networkingSystem;
    private RequestMapper requestMapper;
    private SelectedRoutingAlgorithmMapper selectedRoutingAlgorithmMapper;
    private NCRequestDataMapper ncRequestDataMapper;
    private PathMapper pathMapper;
    private Random random;

    @Before
    public void setup() {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.OFF);
        Logger.getLogger("de.tum.ei.lkn.eces.core").setLevel(Level.OFF);

        controller = new Controller();
        random = new SecureRandom();

        // Systems
        graphSystem = new GraphSystem(controller);
        networkingSystem = new NetworkingSystem(controller, graphSystem);

        // Mappers
        qjumpConfigMapper = new QJumpConfigMapper(controller);
        requestMapper = new RequestMapper(controller);
        selectedRoutingAlgorithmMapper = new SelectedRoutingAlgorithmMapper(controller);
        ncRequestDataMapper = new NCRequestDataMapper(controller);
        pathMapper = new PathMapper(controller);
    }

    @Test
    public void generalTestForEverything() {
        // Systems
        new DNMSystem(controller);
        new RoutingSystem(controller);
        RoutingAlgorithm dijkstra = new DijkstraAlgorithm(controller);

        for(int n : new int[]{1, 5, 100}) {
            for(int p : new int[]{306, 900, 1400}) {
                for(double r : new double[]{1e8, 1e9, 1e10}) {
                    for(double epsilon : new double[]{1e-8, 4e-6, 5e-5}) {
                        QJumpConfig modelConfig = new QJumpConfig(n, epsilon, p, r);
                        QJumpProxy proxy = new QJumpProxy(controller);
                        dijkstra.setProxy(proxy);

                        int N_TESTS = 10;
                        int nAcceptedFlows = 0;
                        for(int i = 0; i < N_TESTS; i++) {
                            double queueSize = random.nextDouble() * 30000000; // up to 30MB
                            int networkSize = random.nextInt(10) + 2;
                            double flowRate = random.nextDouble() * modelConfig.getMaximumRate() * 2; // so that we have some too high
                            double flowBurst = random.nextDouble() * modelConfig.getMaximumPacketSize() * 2; // same
                            double deadline = random.nextDouble() * modelConfig.getGuaranteedDelay() * 2; // same

                            // Create network
                            Network myNetwork = networkingSystem.createNetwork();
                            qjumpConfigMapper.attachComponent(myNetwork.getQueueGraph(), modelConfig);

                            NetworkTopology networkTopology = new GridRandom(networkingSystem, myNetwork, networkSize, networkSize, r, 0, new double[]{queueSize});

                            UnicastRequest request = TestUtils.getRandomUnicastRequest(networkTopology.getNodesAllowedToSend(), networkTopology.getNodesAllowedToReceive());
                            ArrivalCurve tb = CurvePwAffine.getFactory().createTokenBucket(flowRate, flowBurst);
                            NCRequestData ncRequestData = new NCRequestData(tb, Num.getFactory().create(deadline));

                            Entity entity = controller.createEntity();
                            try (MapperSpace mapperSpace = controller.startMapperSpace()) {
                                requestMapper.attachComponent(entity, request);
                                ncRequestDataMapper.attachComponent(entity, ncRequestData);
                                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(dijkstra));
                            }

                            if(nAcceptedFlows < modelConfig.getMaxNumberOfHosts()
                                && flowRate <= modelConfig.getMaximumRate()
                                && flowBurst <= modelConfig.getMaximumPacketSize()
                                && deadline >= modelConfig.getGuaranteedDelay()) {
                                assertTrue(pathMapper.isIn(entity)); // should be accepted
                                nAcceptedFlows++;
                                assertEquals(nAcceptedFlows, proxy.getNumberOfFlows());
                            }
                            else {
                                assertFalse(pathMapper.isIn(entity)); // should not be accepted
                            }
                        }
                    }
                }
            }
        }
    }
}

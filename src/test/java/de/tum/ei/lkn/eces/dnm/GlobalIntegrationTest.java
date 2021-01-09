package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.MapperSpace;
import de.tum.ei.lkn.eces.dnm.config.ACModel;
import de.tum.ei.lkn.eces.dnm.config.BurstIncreaseModel;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.dnm.config.DetServConfig;
import de.tum.ei.lkn.eces.dnm.config.costmodels.functions.*;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.Constant;
import de.tum.ei.lkn.eces.dnm.config.costmodels.values.QueuePriority;
import de.tum.ei.lkn.eces.dnm.mappers.DetServConfigMapper;
import de.tum.ei.lkn.eces.dnm.mappers.NCRequestDataMapper;
import de.tum.ei.lkn.eces.dnm.proxies.DetServProxy;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.MHM.MHMRateRatiosAllocation;
import de.tum.ei.lkn.eces.dnm.resourcemanagement.resourceallocation.TBM.TBMDelayRatiosAllocation;
import de.tum.ei.lkn.eces.graph.GraphSystem;
import de.tum.ei.lkn.eces.network.Link;
import de.tum.ei.lkn.eces.network.Network;
import de.tum.ei.lkn.eces.network.NetworkNode;
import de.tum.ei.lkn.eces.network.NetworkingSystem;
import de.tum.ei.lkn.eces.routing.RoutingSystem;
import de.tum.ei.lkn.eces.routing.SelectedRoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.csp.unicast.cbf.CBFAlgorithm;
import de.tum.ei.lkn.eces.routing.mappers.RequestMapper;
import de.tum.ei.lkn.eces.routing.mappers.ResponseMapper;
import de.tum.ei.lkn.eces.routing.mappers.SelectedRoutingAlgorithmMapper;
import de.uni_kl.cs.discodnc.curves.CurvePwAffine;
import de.uni_kl.cs.discodnc.numbers.Num;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GlobalIntegrationTest {
	@Test
	public final void bigRandomTest() {
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.ERROR);
		Logger.getLogger("de.tum.ei.lkn.eces.core").setLevel(Level.OFF);

		for (ACModel acModel : ACModel.values()) {
			for (ResidualMode residualMode : ResidualMode.values()) {
				for (BurstIncreaseModel burstIncrease : BurstIncreaseModel.values()) {
					for (boolean inputLinkShaping : new boolean[]{false, true}) {
						for (CostModel costModel : new CostModel[]{
								new Constant(),
								new LowerLimit(new UpperLimit(new Summation(new Constant(),new Division(new Constant(),new Summation( new QueuePriority(),new Constant()))),2),1),
								new LowerLimit(new UpperLimit(new Minus(new Constant(4), new QueuePriority()),4),1)}) {
							try {
								Controller controller = new Controller();
								DetServConfig modelingConfig = new DetServConfig(
										acModel,
										residualMode,
										burstIncrease,
										inputLinkShaping,
										costModel.init(controller),
										(cont, sched) -> {
										    if (acModel == ACModel.TBM)
										        return new TBMDelayRatiosAllocation(cont);
										    else
										        return new MHMRateRatiosAllocation(cont);
										});

								GraphSystem graphSystem = new GraphSystem(controller);
								NetworkingSystem networkingSystem = new NetworkingSystem(controller, graphSystem);
								ResponseMapper responseMapper = new ResponseMapper(controller);
								RequestMapper requestMapper = new RequestMapper(controller);
								DetServConfigMapper modelingConfigMapper = new DetServConfigMapper(controller);
								NCRequestDataMapper ncRequestDataMapper = new NCRequestDataMapper(controller);
								SelectedRoutingAlgorithmMapper selectedRoutingAlgorithmMapper = new SelectedRoutingAlgorithmMapper(controller);
								new RoutingSystem(controller);
								new DNMSystem(controller);
								DetServProxy detServProxy = new DetServProxy(controller);
								CBFAlgorithm cbf = new CBFAlgorithm(controller);
								cbf.setProxy(detServProxy);

								Network network = networkingSystem.createNetwork();
								modelingConfigMapper.attachComponent(network.getQueueGraph(), modelingConfig);
                                modelingConfig.initCostModel(controller);

								NetworkNode[] nodes = new NetworkNode[8];
								for (int i = 0; i < nodes.length; i++)
									nodes[i] = networkingSystem.createNode(network);

								Link[] links = new Link[10];
								links[0] = networkingSystem.createLinkWithPriorityScheduling(nodes[0], nodes[1], 128000000.0, 10.0, new double[]{60000.0, 60000.0, 60000.0});
								links[1] = networkingSystem.createLinkWithPriorityScheduling(nodes[1], nodes[2], 128000000.0, 40.0, new double[]{60000.0});
								links[2] = networkingSystem.createLinkWithPriorityScheduling(nodes[2], nodes[3], 128000000.0, 10.0, new double[]{60000.0, 60000.0, 60000.0});
								links[3] = networkingSystem.createLinkWithPriorityScheduling(nodes[3], nodes[0], 128000000.0, 0.0, new double[]{60000.0});
								links[4] = networkingSystem.createLinkWithPriorityScheduling(nodes[4], nodes[5], 128000000.0, 0.0, new double[]{60000.0, 60000.0, 60000.0});
								links[5] = networkingSystem.createLinkWithPriorityScheduling(nodes[5], nodes[6], 128000000.0, 0.0, new double[]{60000.0, 0});
								links[6] = networkingSystem.createLinkWithPriorityScheduling(nodes[6], nodes[7], 128000000.0, 0.0, new double[]{60000.0});
								links[7] = networkingSystem.createLinkWithPriorityScheduling(nodes[7], nodes[0], 128000000.0, 2.1, new double[]{60000.0, 60000.0, 60000.0});
								links[8] = networkingSystem.createLinkWithPriorityScheduling(nodes[0], nodes[4], 128000000.0, 0.0, new double[]{60000.0});
								links[9] = networkingSystem.createLinkWithPriorityScheduling(nodes[6], nodes[2], 128000000.0, 0.0, new double[]{60000.0, 60000.0, 60000.0});

								Entity entity;

								for(int run = 0; run < TestUtils.NUMBER_OF_POINTS_TEST; run++) {
									entity = controller.createEntity();
									try (MapperSpace mapperSpace = controller.startMapperSpace()) {
										requestMapper.attachComponent(entity, TestUtils.getRandomUnicastRequest(nodes, nodes));
										ncRequestDataMapper.attachComponent(entity, new NCRequestData(
												CurvePwAffine.getFactory().createTokenBucket(TestUtils.randomRate(), TestUtils.randomBurst()),
												Num.getFactory().create(TestUtils.randomDelay())));
										selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cbf));
									}

									// Just checking for a response: it could be an error or a path.
									assertTrue(responseMapper.isIn(entity));

									// Removing some (1/30) of them
									if(run % (TestUtils.NUMBER_TESTS / 30) == 0) {
										try (MapperSpace mapperSpace = controller.startMapperSpace()) {
											requestMapper.detachComponent(entity);
										}
									}
								}
							} catch (Exception e) {
								fail("Exception for: " + acModel + " " + residualMode + " " + burstIncrease + " " + inputLinkShaping + " " + costModel);
							}
						}
					}
				}
			}
		}
	}
}

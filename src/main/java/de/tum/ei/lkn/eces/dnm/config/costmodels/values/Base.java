package de.tum.ei.lkn.eces.dnm.config.costmodels.values;

import de.tum.ei.lkn.eces.core.Component;
import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.Mapper;
import de.tum.ei.lkn.eces.dnm.config.CostModel;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.Scheduler;
import de.tum.ei.lkn.eces.network.mappers.QueueMapper;
import de.tum.ei.lkn.eces.network.mappers.SchedulerMapper;

/**
 * Base class for any cost value.
 *
 * @author Jochen Guck
 * @author Amaury Van Bemten
 */
abstract class Base extends CostModel {
	private Mapper<Queue> queueMapper;
	private Mapper<Scheduler> schedulerMapper;

	protected Base() {
	}

	protected Base(Controller controller) {
		queueMapper = new QueueMapper(controller);
		schedulerMapper = new SchedulerMapper(controller);
	}

	protected Mapper<Queue> getQueueMapper() {
		if(queueMapper == null)
			throw new RuntimeException("The cost model must be initiated (init) with the used controller!");
		else
			return queueMapper;
	}

	protected Mapper<Scheduler> getSchedulerMapper() {
		if(schedulerMapper == null)
			throw new RuntimeException("The cost model must be initiated (init) with the used controller!");
		else
			return schedulerMapper;
	}

	protected Entity getSchedulerEntity(Component component){
		return getSchedulerEntity(component.getEntity());
	}

	protected Entity getSchedulerEntity(Entity entity) {
		Queue queue = getQueueMapper().getOptimistic(entity);
		if(queue == null)
			return getSchedulerMapper().getOptimistic(entity).getEntity();
		else
			return queue.getScheduler().getEntity();
	}

}

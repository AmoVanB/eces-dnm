package de.tum.ei.lkn.eces.dnm;

import de.tum.ei.lkn.eces.graph.Node;
import de.tum.ei.lkn.eces.network.NetworkNode;
import de.tum.ei.lkn.eces.routing.requests.UnicastRequest;

import java.util.Random;

public class TestUtils {
	public static int NUMBER_TESTS = 50000;
	public static int NUMBER_OF_POINTS_TEST = 100;

	private static Random random = new Random();

	public static double randomBurst() {
		// Rounded at Byte
		return Math.round((random.nextDouble() * 5000));
	}

	public static double randomDelay() {
		// Round at ns
		return Math.floor((random.nextDouble() * 0.5) * 1000000000)/1000000000;
	}

	public static double randomRate() {
		return Math.round(random.nextDouble() * 10000000000f);
	}

	public static UnicastRequest getRandomUnicastRequest(NetworkNode[] senders, NetworkNode[] receivers) {
		Node src = null;
		Node dest = null;
		while (src == dest) {
			int srcnum = (int) (((double) senders.length) * random.nextDouble());
			int destnum = (int) (((double) receivers.length) * random.nextDouble());
			src = senders[srcnum].getQueueNode();
			dest = receivers[destnum].getQueueNode();
		}

		return new UnicastRequest(src, dest);
	}
}

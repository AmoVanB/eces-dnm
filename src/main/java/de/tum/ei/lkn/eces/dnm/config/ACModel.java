package de.tum.ei.lkn.eces.dnm.config;

/**
 * The two different DetServ [1] access control models.
 *
 * Chameleon [2] corresponds to the threshold-based model (TBM) and Silo [3] corresponds to a particular configuration
 * of it.
 *
 * [1] Guck, Jochen W., Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS
 * provisioning in SDN-based industrial environments." IEEE Transactions on Network and Service Management 14, no. 4
 * (2017): 1003-1017.
 * [2] Van Bemten, Amaury, Nemanja Ðerić, Amir Varasteh, Stefan Schmid, Carmen Mas-Machuca, Andreas Blenk, and Wolfgang
 * Kellerer. "Chameleon: predictable latency and high utilization with queue-aware and adaptive source routing." In
 * Proceedings of the 16th International Conference on emerging Networking EXperiments and Technologies, pp. 451-465.
 * 2020.
 * [3] Jang, Keon, Justine Sherry, Hitesh Ballani, and Toby Moncaster. "Silo: Predictable message latency in the cloud."
 * ACM SIGCOMM Computer Communication Review 45, no. 4 (2015): 435-448.
 */
public enum ACModel {
	MHM,
	TBM,
}

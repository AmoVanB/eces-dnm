package de.tum.ei.lkn.eces.dnm.config;

/**
 * The two different DetServ [1] access control models.
 *
 * Note that the threshold-based model (TBM) includes Silo [2].
 *
 * [1] Guck, Jochen W., Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS
 * provisioning in SDN-based industrial environments." IEEE Transactions on Network and Service Management 14, no. 4
 * (2017): 1003-1017.
 * [2] Jang, Keon, Justine Sherry, Hitesh Ballani, and Toby Moncaster. "Silo: Predictable message latency in the cloud."
 * ACM SIGCOMM Computer Communication Review 45, no. 4 (2015): 435-448.
 */
public enum ACModel {
	MHM,
	TBM,
}

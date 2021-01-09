package de.tum.ei.lkn.eces.dnm.config;

/**
 * Different ways of taking into account the burst increase of a flow along its path. See [1]
 *
 * [1] Guck, Jochen W., Amaury Van Bemten, and Wolfgang Kellerer. "DetServ: Network models for real-time QoS
 * provisioning in SDN-based industrial environments." IEEE Transactions on Network and Service Management 14, no. 4
 * (2017): 1003-1017.
 *
 */
public enum BurstIncreaseModel {
	NO, // not computed
	WORST_CASE_BURST, // computed with the deadline
	WORST_CASE_BURST_REAL_RESERVATION, // computed with the deadline but reservation with the real queue delay
	REAL // real computation with delay so far for both access control and reservation
}

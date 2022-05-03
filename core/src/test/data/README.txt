1. python-1.2.2 is the output of test_end_to_end_election.py from python 1.2.2 on 7/1/2021 HEAD.
1.1 python-1.4.0 is the output of test_end_to_end_election.py from python 1.4.0 on 1/15/2022 HEAD.

2. publishEndToEnd is the output of TestEndToEndElectionIntegration.java from 2/15/2022. This
     uses the standard library classes, serializing to json.

3. workflow is the output of RunRemoteWorkflow from 4/30/2022, using start
      This uses the remote library code, serializing to proto.

Note: election constants changed python PR #387, 8/4/21.
Note: hash changed to using mod(Q) instead of mod(Q-1) on 2/24/2022.


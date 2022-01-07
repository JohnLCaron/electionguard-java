1. python-1.2.2 is the output of test_end_to_end_election.py from python 1.2.2 on 7/1/2021 HEAD.

2. publishEndToEnd is the output of TestEndToEndElectionIntegration.java from 7/1/2021 HEAD. This
     uses the main library code, serializing to json.

3. decryptor is the remoteWorkflow/decryptor output, encryptor is the remoteWorkflow/encryptor output,
      keyCeremony is the remoteWorkflow/keyCeremony output of RunRemoteWorkflow from 7/1/2021 HEAD.
      This uses the remote library code, serializing to proto.

Note: election constants changed python PR #387, 8/4/21. Test output needs to be regenerated.
    REGEN 1/7/22: src/test/data/workflow/
    REGEN 1/7/22: src/test/data/publishEndToEnd/
    public static final String topdirJsonPython = "src/test/data/python-1.2.2/";

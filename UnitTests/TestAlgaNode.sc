TestAlgaNode : UnitTest {
    test_AlgaBoot {
		var server = Server.local;
        Alga.boot({
			this.assert(server.serverRunning)
		}, server);
    }
}
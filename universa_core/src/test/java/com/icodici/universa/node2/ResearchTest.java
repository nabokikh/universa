package com.icodici.universa.node2;



import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


public class ResearchTest extends BaseNetworkTest {

    private static Network network_s = null;
    private static Node node_s = null;
    private static Ledger ledger_s = null;
    private static Config config_s = null;



    @BeforeClass
    public static void beforeClass() throws Exception {
        initTestSet();
    }

    private static void initTestSet() throws Exception {
        initTestSet(1, 1);
    }

    private static void initTestSet(int posCons, int negCons) throws Exception {
        config_s = new Config();

        // The quorum bigger than the network capacity: we model the situation
        // when the system will not get the answer
        config_s.setPositiveConsensus(posCons);
        config_s.setNegativeConsensus(negCons);
        config_s.setResyncBreakConsensus(1);

        NodeInfo myInfo = new NodeInfo(getNodePublicKey(0), 1, "node1", "localhost",
                7101, 7102, 7104);
        NetConfig nc = new NetConfig(asList(myInfo));
        network_s = new TestSingleNetwork(nc);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        ledger_s = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        node_s = new Node(config_s, myInfo, ledger_s, network_s);
        ((TestSingleNetwork)network_s).addNode(myInfo, node_s);
    }



    @Before
    public void setUp() throws Exception {
        init(node_s, null, network_s, ledger_s, config_s);
    }



    @Test
    public void noQourumError() throws Exception {
        initTestSet(2, 2);
        setUp();

        TestItem item = new TestItem(true);

//        LogPrinter.showDebug(true);
        node.registerItem(item);
        try {
            node.waitItem(item.getId(), 100);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        @NonNull ItemResult checkedItem = node.checkItem(item.getId());

        assertEquals(ItemState.PENDING_POSITIVE, checkedItem.state);
        assertTrue(checkedItem.expiresAt.isBefore(ZonedDateTime.now().plusHours(5)));

        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        try {
            node.waitItem(item2.getId(), 100);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        checkedItem = node.checkItem(item2.getId());

        initTestSet(1, 1);

        assertEquals(ItemState.PENDING_NEGATIVE, checkedItem.state);
    }


    @Test
    public void test1() throws Exception {
        Config config = new Config();

        config.setPositiveConsensus(1);
        config.setNegativeConsensus(1);

        NodeInfo nodeInfo = new NodeInfo(getNodePublicKey(0),1,"node1","localhost",7101,7102,7104);
        NetConfig netConfig = new NetConfig(asList(nodeInfo));
        Network network = new TestSingleNetwork(netConfig);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        Ledger ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        Node node = new Node(config, nodeInfo, ledger, network);
        System.out.println(node.toString());
    }



    @Test
    public void quantiserTest() throws Exception {
        Quantiser quantiser = new Quantiser();
        quantiser.reset(10);
        quantiser.addWorkCost(Quantiser.QuantiserProcesses.PRICE_APPLICABLE_PERM);
        quantiser.addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
        try {
            quantiser.addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            assertFalse(true); // must throw QuantiserException
        } catch (Quantiser.QuantiserException e) {
            return;
        }
    }



    @Test
    public void quantiserInContract() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        c.check();
        c.traceErrors();
        assertTrue(c.check());
        c.seal();
        System.out.println(c.getProcessedCost());
    }







//    protected static final String ROOT_PATH = "./src/test_contracts/";
//    protected static final String CONFIG_2_PATH = "./src/test_config_2/";

}

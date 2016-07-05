package hudson.plugins.ec2;

import com.amazonaws.AmazonClientException;

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.NodeProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class EC2RetentionStrategyTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    final AtomicBoolean idleTimeoutCalled = new AtomicBoolean(false);

    @Test
    public void testOnBillingHourRetention() throws Exception {
        EC2RetentionStrategy rs = new EC2RetentionStrategy("-2");
        List<int[]> upTime = new ArrayList<int[]>();
        List<Boolean> expected = new ArrayList<Boolean>();
        upTime.add(new int[] { 58, 0 });
        expected.add(true);
        upTime.add(new int[] { 57, 59 });
        expected.add(false);
        upTime.add(new int[] { 59, 00 });
        expected.add(true);
        upTime.add(new int[] { 59, 30 });
        expected.add(true);
        upTime.add(new int[] { 60, 00 });
        expected.add(false);

        for (int i = 0; i < upTime.size(); i++) {
            int[] t = upTime.get(i);
            EC2Computer computer = computerWithIdleTime(t[0], t[1]);
            rs.check(computer);
            assertEquals("Expected " + t[0] + "m" + t[1] + "s to be " + expected.get(i), (boolean) expected.get(i), idleTimeoutCalled.get());
            // reset the assumption
            idleTimeoutCalled.set(false);
        }
    }

    public void testRetentionWhenQueueHasWaitingItemForThisNode() throws Exception {
        EC2RetentionStrategy rs = new EC2RetentionStrategy("-2");
        EC2Computer computer = computerWithIdleTime(59, 0);
        final Label selfLabel = computer.getNode().getSelfLabel();
        final Queue queue = Jenkins.getInstance().getQueue();
        final Task task = taskForLabel(selfLabel, false);
        queue.schedule(task, 500);
        rs.check(computer);
        assertFalse("Expected computer to be left running", idleTimeoutCalled.get());
        queue.cancel(task);
        rs.check(computer);
        assertTrue("Expected computer to be idled", idleTimeoutCalled.get());
    }

    public void testRetentionWhenQueueHasBlockedItemForThisNode() throws Exception {
        EC2RetentionStrategy rs = new EC2RetentionStrategy("-2");
        EC2Computer computer = computerWithIdleTime(59, 0);
        final Label selfLabel = computer.getNode().getSelfLabel();
        final Queue queue = Jenkins.getInstance().getQueue();
        final Task task = taskForLabel(selfLabel, true);
        queue.schedule(task, 0);
        rs.check(computer);
        assertFalse("Expected computer to be left running", idleTimeoutCalled.get());
        queue.cancel(task);
        rs.check(computer);
        assertTrue("Expected computer to be idled", idleTimeoutCalled.get());
    }

    private EC2Computer computerWithIdleTime(final int minutes, final int seconds) throws Exception {
        final EC2AbstractSlave slave = new EC2AbstractSlave("name", "id", "description", "fs", 1, null, "label", null, null, "init", "tmpDir", new ArrayList<NodeProperty<?>>(), "remote", "jvm", false, "idle", null, "cloud", false, false, Integer.MAX_VALUE, null) {
            @Override
            public void terminate() {
            }

            @Override
            public String getEc2Type() {
                return null;
            }

            @Override
            void idleTimeout() {
                idleTimeoutCalled.set(true);
            }
        };
        EC2Computer computer = new EC2Computer(slave) {

            @Override
            public EC2AbstractSlave getNode() {
                return slave;
            }

            @Override
            public long getUptime() throws AmazonClientException, InterruptedException {
                return ((minutes * 60L) + seconds) * 1000L;
            }

            @Override
            public boolean isOffline() {
                return false;
            }
            
            @Override
            public InstanceState getState() {
                return InstanceState.RUNNING;
            }
        };
        assertTrue(computer.isIdle());
        assertTrue(computer.isOnline());
        return computer;
    }

    private Queue.Task taskForLabel(final Label label, boolean blocked) {
        final CauseOfBlockage cob = blocked ? new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return "Blocked";
            }
        } : null;
        return new AbstractQueueTask() {
            public ResourceList getResourceList() {
                return null;
            }

            public Node getLastBuiltOn() {
                return null;
            }

            public long getEstimatedDuration() {
                return -1;
            }

            public Label getAssignedLabel() {
                return label;
            }

            public Executable createExecutable() throws IOException {
                return null;
            }

            public String getDisplayName() {
                return null;
            }

            @Override
            public CauseOfBlockage getCauseOfBlockage() {
                return cob;
            }

            public boolean isBuildBlocked() {
                return cob != null;
            }

            public boolean hasAbortPermission() {
                return false;
            }

            public String getWhyBlocked() {
                return cob == null ? null : cob.getShortDescription();
            }

            public String getUrl() {
                return null;
            }

            public String getName() {
                return null;
            }

            public String getFullDisplayName() {
                return null;
            }

            public void checkAbortPermission() {
            }
        };
    }
}

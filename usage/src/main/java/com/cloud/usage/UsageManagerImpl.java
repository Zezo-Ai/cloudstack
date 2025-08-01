// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.usage;

import static com.cloud.utils.NumbersUtil.toHumanReadableSize;

import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.cloud.network.Network;
import com.cloud.usage.dao.UsageNetworksDao;
import com.cloud.usage.parser.UsageParser;
import com.cloud.network.vpc.Vpc;
import com.cloud.usage.dao.UsageVpcDao;
import javax.inject.Inject;
import javax.naming.ConfigurationException;
import javax.persistence.EntityExistsException;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.quota.QuotaAlertManager;
import org.apache.cloudstack.quota.QuotaManager;
import org.apache.cloudstack.quota.QuotaStatement;
import org.apache.cloudstack.usage.UsageTypes;
import org.apache.cloudstack.utils.usage.UsageUtils;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import com.cloud.alert.AlertManager;
import com.cloud.event.EventTypes;
import com.cloud.event.UsageEventDetailsVO;
import com.cloud.event.UsageEventVO;
import com.cloud.event.dao.UsageEventDao;
import com.cloud.event.dao.UsageEventDetailsDao;
import com.cloud.usage.dao.BucketStatisticsDao;
import com.cloud.usage.dao.UsageBackupDao;
import com.cloud.usage.dao.UsageDao;
import com.cloud.usage.dao.UsageIPAddressDao;
import com.cloud.usage.dao.UsageJobDao;
import com.cloud.usage.dao.UsageLoadBalancerPolicyDao;
import com.cloud.usage.dao.UsageNetworkDao;
import com.cloud.usage.dao.UsageNetworkOfferingDao;
import com.cloud.usage.dao.UsagePortForwardingRuleDao;
import com.cloud.usage.dao.UsageSecurityGroupDao;
import com.cloud.usage.dao.UsageStorageDao;
import com.cloud.usage.dao.UsageVMInstanceDao;
import com.cloud.usage.dao.UsageVMSnapshotDao;
import com.cloud.usage.dao.UsageVMSnapshotOnPrimaryDao;
import com.cloud.usage.dao.UsageVPNUserDao;
import com.cloud.usage.dao.UsageVmDiskDao;
import com.cloud.usage.dao.UsageVolumeDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserStatisticsVO;
import com.cloud.user.VmDiskStatisticsVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserStatisticsDao;
import com.cloud.user.dao.VmDiskStatisticsDao;
import com.cloud.utils.DateUtil;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;

@Component
public class UsageManagerImpl extends ManagerBase implements UsageManager, Runnable {

    protected static final String DAILY = "DAILY";
    protected static final String WEEKLY = "WEEKLY";
    protected static final String MONTHLY = "MONTHLY";

    private static final int HOURLY_TIME = 60;
    private static final int DAILY_TIME = 60 * 24;
    private static final int THREE_DAYS_IN_MINUTES = 60 * 24 * 3;

    @Inject
    private AccountDao _accountDao;
    @Inject
    private UserStatisticsDao _userStatsDao;
    @Inject
    private UsageDao _usageDao;
    @Inject
    private UsageVMInstanceDao _usageInstanceDao;
    @Inject
    private UsageIPAddressDao _usageIPAddressDao;
    @Inject
    private UsageNetworkDao _usageNetworkDao;
    @Inject
    private UsageVolumeDao _usageVolumeDao;
    @Inject
    private UsageStorageDao _usageStorageDao;
    @Inject
    private UsageLoadBalancerPolicyDao _usageLoadBalancerPolicyDao;
    @Inject
    private UsagePortForwardingRuleDao _usagePortForwardingRuleDao;
    @Inject
    private UsageNetworkOfferingDao _usageNetworkOfferingDao;
    @Inject
    private UsageVPNUserDao _usageVPNUserDao;
    @Inject
    private UsageSecurityGroupDao _usageSecurityGroupDao;
    @Inject
    private UsageJobDao _usageJobDao;
    @Inject
    private VmDiskStatisticsDao _vmDiskStatsDao;
    @Inject
    private UsageVmDiskDao _usageVmDiskDao;
    @Inject
    protected AlertManager _alertMgr;
    @Inject
    protected UsageEventDao _usageEventDao;
    @Inject
    protected UsageEventDetailsDao _usageEventDetailsDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    private UsageVMSnapshotDao _usageVMSnapshotDao;
    @Inject
    private UsageVMSnapshotOnPrimaryDao _usageSnapshotOnPrimaryDao;
    @Inject
    private UsageBackupDao usageBackupDao;
    @Inject
    private QuotaManager _quotaManager;
    @Inject
    private QuotaAlertManager _alertManager;
    @Inject
    private QuotaStatement _quotaStatement;
    @Inject
    private UsageNetworksDao usageNetworksDao;

    @Inject
    private BucketStatisticsDao _bucketStatisticsDao;

    @Inject
    private UsageVpcDao usageVpcDao;

    @Inject
    private List<UsageParser> usageParsers;

    private String _version = null;
    private final Calendar _jobExecTime = Calendar.getInstance();
    private int _aggregationDuration = 0;
    private int _sanityCheckInterval = 0;
    private boolean _runQuota=false;
    String _hostname = null;
    int _pid = 0;
    private final GlobalLock _heartbeatLock = GlobalLock.getInternLock("usage.job.heartbeat.check");
    private final List<UsageNetworkVO> usageNetworks = new ArrayList<UsageNetworkVO>();
    private final List<UsageVmDiskVO> usageVmDisks = new ArrayList<UsageVmDiskVO>();

    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Usage-Job"));
    private final ScheduledExecutorService _heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Usage-HB"));
    private final ScheduledExecutorService _sanityExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Usage-Sanity"));
    private Future _scheduledFuture = null;
    private Future _heartbeat = null;
    private Future _sanity = null;
    private boolean  usageSnapshotSelection = false;

    private static TimeZone usageAggregationTimeZone = TimeZone.getTimeZone("GMT");

    public UsageManagerImpl() {
    }

    private void mergeConfigs(Map<String, String> dbParams, Map<String, Object> xmlParams) {
        for (Map.Entry<String, Object> param : xmlParams.entrySet()) {
            dbParams.put(param.getKey(), (String)param.getValue());
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        final String run = "usage.vmops.pid";

        if (logger.isDebugEnabled()) {
            logger.debug("Checking to see if " + run + " exists.");
        }

        final Class<?> c = UsageServer.class;
        _version = c.getPackage().getImplementationVersion();
        if (_version == null) _version="unknown";

        if (logger.isInfoEnabled()) {
            logger.info("Implementation Version is " + _version);
        }

        Map<String, String> configs;
        try {
            configs = _configDao.getConfiguration(params);

            if (params != null) {
                mergeConfigs(configs, params);
                logger.info("configs = " + configs);
            }
        } catch (CloudRuntimeException e) {
            logger.error("Unhandled configuration exception: " + e.getMessage());
            throw new CloudRuntimeException("Unhandled configuration exception", e);
        }

        String execTime = configs.get("usage.stats.job.exec.time");
        String aggregationRange = configs.get("usage.stats.job.aggregation.range");
        String execTimeZone = configs.get("usage.execution.timezone");
        String aggregationTimeZone = configs.get("usage.aggregation.timezone");
        String sanityCheckInterval = configs.get("usage.sanity.check.interval");
        String quotaEnable = configs.get("quota.enable.service");
        _runQuota = Boolean.valueOf(quotaEnable == null ? "false" : quotaEnable );
        usageSnapshotSelection  = Boolean.valueOf(configs.get("usage.snapshot.virtualsize.select"));
        if (sanityCheckInterval != null) {
            _sanityCheckInterval = Integer.parseInt(sanityCheckInterval);
        }

        if (aggregationTimeZone != null && !aggregationTimeZone.isEmpty()) {
            usageAggregationTimeZone = TimeZone.getTimeZone(aggregationTimeZone);
        }

        try {
            if ((execTime == null) || (aggregationRange == null)) {
                logger.error("missing configuration values for usage job, usage.stats.job.exec.time = " + execTime + ", usage.stats.job.aggregation.range = " +
                        aggregationRange);
                throw new ConfigurationException("Missing configuration values for usage job, usage.stats.job.exec.time = " + execTime +
                        ", usage.stats.job.aggregation.range = " + aggregationRange);
            }
            String[] execTimeSegments = execTime.split(":");
            if (execTimeSegments.length != 2) {
                logger.error("Unable to parse usage.stats.job.exec.time");
                throw new ConfigurationException("Unable to parse usage.stats.job.exec.time '" + execTime + "'");
            }
            int hourOfDay = Integer.parseInt(execTimeSegments[0]);
            int minutes = Integer.parseInt(execTimeSegments[1]);

            Date currentDate = new Date();
            _jobExecTime.setTime(currentDate);

            _jobExecTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            _jobExecTime.set(Calendar.MINUTE, minutes);
            _jobExecTime.set(Calendar.SECOND, 0);
            _jobExecTime.set(Calendar.MILLISECOND, 0);

            TimeZone jobExecTimeZone = execTimeZone != null ? TimeZone.getTimeZone(execTimeZone) : Calendar.getInstance().getTimeZone();
            _jobExecTime.setTimeZone(jobExecTimeZone);

            // if the hour to execute the job has already passed, roll the day forward to the next day
            if (_jobExecTime.getTime().before(currentDate)) {
                _jobExecTime.roll(Calendar.DAY_OF_YEAR, true);
            }

            logger.info("Usage is configured to execute in time zone [{}], at [{}], each [{}] minutes; the current time in that timezone is [{}] and the " +
                            "next job is scheduled to execute at [{}]. During its execution, Usage will aggregate stats according to the time zone [{}] defined in global setting [usage.aggregation.timezone].",
                    jobExecTimeZone.getID(), execTime, aggregationRange, DateUtil.displayDateInTimezone(jobExecTimeZone, currentDate),
                    DateUtil.displayDateInTimezone(jobExecTimeZone, _jobExecTime.getTime()), usageAggregationTimeZone.getID());

            _aggregationDuration = Integer.parseInt(aggregationRange);
            if (_aggregationDuration < UsageUtils.USAGE_AGGREGATION_RANGE_MIN) {
                logger.warn("Usage stats job aggregation range is to small, using the minimum value of " + UsageUtils.USAGE_AGGREGATION_RANGE_MIN);
                _aggregationDuration = UsageUtils.USAGE_AGGREGATION_RANGE_MIN;
            }
            _hostname = InetAddress.getLocalHost().getHostName() + "/" + InetAddress.getLocalHost().getHostAddress();
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Unable to parse usage.stats.job.exec.time '" + execTime + "' or usage.stats.job.aggregation.range '" + aggregationRange +
                    "', please check configuration values");
        } catch (Exception e) {
            logger.error("Unhandled exception configuring UsageManger", e);
            throw new ConfigurationException("Unhandled exception configuring UsageManager " + e.toString());
        }

        try {
            _pid = (int) ProcessHandle.current().pid();
        } catch (Exception e) {
            String msg = String.format("Unable to get process Id for %s!", e.toString());
            logger.debug(msg);
            throw new ConfigurationException(msg);
        }
        return true;
    }

    public static TimeZone getUsageAggregationTimeZone() {
        return usageAggregationTimeZone;
    }

    @Override
    public boolean start() {
        if (logger.isInfoEnabled()) {
            logger.info("Starting Usage Manager");
        }

        _usageJobDao.removeLastOpenJobsOwned(_hostname, 0);
        Runtime.getRuntime().addShutdownHook(new AbandonJob());

        // use the configured exec time and aggregation duration for scheduling the job
        _scheduledFuture =
                _executor.scheduleAtFixedRate(this, _jobExecTime.getTimeInMillis() - System.currentTimeMillis(), _aggregationDuration * 60 * 1000, TimeUnit.MILLISECONDS);

        _heartbeat =
                _heartbeatExecutor.scheduleAtFixedRate(new Heartbeat(), /* start in 15 seconds...*/15 * 1000, /* check database every minute*/60 * 1000,
                        TimeUnit.MILLISECONDS);

        if (_sanityCheckInterval > 0) {
            _sanity = _sanityExecutor.scheduleAtFixedRate(new SanityCheck(), 1, _sanityCheckInterval, TimeUnit.DAYS);
        }

        TransactionLegacy usageTxn = TransactionLegacy.open(TransactionLegacy.USAGE_DB);
        try {
            if (_heartbeatLock.lock(3)) { // 3 second timeout
                try {
                    UsageJobVO job = _usageJobDao.getLastJob();
                    if (job == null) {
                        _usageJobDao.createNewJob(_hostname, _pid, UsageJobVO.JOB_TYPE_RECURRING);
                    }
                } finally {
                    _heartbeatLock.unlock();
                }
            } else {
                if (logger.isTraceEnabled())
                    logger.trace("Heartbeat lock is in use by others, returning true as someone else will take over the job if required");
            }
        } finally {
            usageTxn.close();
        }

        return true;
    }

    @Override
    public boolean stop() {
        _heartbeat.cancel(true);
        _scheduledFuture.cancel(true);
        if (_sanity != null) {
            _sanity.cancel(true);
        }

        return true;
    }


    @Override
    public void run() {
        (new ManagedContextRunnable() {
            @Override
            protected void runInContext() {
                runInContextInternal();
            }
        }).run();
    }

    protected void runInContextInternal() {
        if (logger.isInfoEnabled()) {
            logger.info("starting usage job...");
        }

        // how about we update the job exec time when the job starts???
        long execTime = _jobExecTime.getTimeInMillis();
        long now = System.currentTimeMillis() + 2000; // 2 second buffer since jobs can run a little early (though usually just by milliseconds)

        if (execTime < now) {
            // if exec time is in the past, calculate the next time the job will execute...if this is a one-off job that is a result
            // of scheduleParse() then don't update the next exec time...
            _jobExecTime.add(Calendar.MINUTE, _aggregationDuration);
        }

        UsageJobVO job = _usageJobDao.isOwner(_hostname, _pid);
        if (job != null) {
            // FIXME: we really need to do a better job of not missing any events...so we should some how
            //        keep track of the last time usage was run, then go from there...
            // For executing the job, we treat hourly and daily as special time ranges, using the previous full hour or the previous
            // full day.  Otherwise we just subtract off the aggregation range from the current time and use that as start date with
            // current time as end date.
            Calendar cal = Calendar.getInstance(usageAggregationTimeZone);
            cal.setTime(new Date());
            long startDate = 0;
            long endDate = 0;
            if (_aggregationDuration == DAILY_TIME) {
                cal.roll(Calendar.DAY_OF_YEAR, false);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                startDate = cal.getTime().getTime();

                cal.roll(Calendar.DAY_OF_YEAR, true);
                cal.add(Calendar.MILLISECOND, -1);
                endDate = cal.getTime().getTime();
            } else if (_aggregationDuration == HOURLY_TIME) {
                cal.roll(Calendar.HOUR_OF_DAY, false);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                startDate = cal.getTime().getTime();

                cal.roll(Calendar.HOUR_OF_DAY, true);
                cal.add(Calendar.MILLISECOND, -1);
                endDate = cal.getTime().getTime();
            } else {
                cal.add(Calendar.MINUTE, -1 * _aggregationDuration);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                startDate = cal.getTime().getTime();

                cal.add(Calendar.MINUTE, _aggregationDuration);
                cal.add(Calendar.MILLISECOND, -1);
                endDate = cal.getTime().getTime();
            }

            parse(job, startDate, endDate);
            if (_runQuota){
                try {
                    _quotaManager.calculateQuotaUsage();
                }
                catch (Exception e){
                    logger.error("Exception received while calculating quota", e);
                }
                try {
                    _quotaStatement.sendStatement();
                } catch (Exception e) {
                    logger.error("Exception received while sending statements", e);
                }
                try {
                    _alertManager.checkAndSendQuotaAlertEmails();
                } catch (Exception e) {
                    logger.error("Exception received while sending alerts", e);
                }
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Not owner of usage job, skipping...");
            }
        }
        if (logger.isInfoEnabled()) {
            logger.info("usage job complete");
        }
    }

    @Override
    public void scheduleParse() {
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduling Usage job...");
        }
        _executor.schedule(this, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public void parse(UsageJobVO job, long startDateMillis, long endDateMillis) {
        // TODO: Shouldn't we also allow parsing by the type of usage?

        boolean success = false;
        long timeStart = System.currentTimeMillis();
        try {
            if ((endDateMillis == 0) || (endDateMillis > timeStart)) {
                endDateMillis = timeStart;
            }

            long lastSuccess = _usageJobDao.getLastJobSuccessDateMillis();
            if (lastSuccess != 0) {
                startDateMillis = lastSuccess + 1; // 1 millisecond after
            }

            if (startDateMillis >= endDateMillis) {
                if (logger.isInfoEnabled()) {
                    logger.info("not parsing usage records since start time mills (" + startDateMillis + ") is on or after end time millis (" + endDateMillis + ")");
                }

                TransactionLegacy jobUpdateTxn = TransactionLegacy.open(TransactionLegacy.USAGE_DB);
                try {
                    jobUpdateTxn.start();
                    // everything seemed to work...set endDate as the last success date
                    _usageJobDao.updateJobSuccess(job.getId(), startDateMillis, endDateMillis, System.currentTimeMillis() - timeStart, success);

                    // create a new job if this is a recurring job
                    if (job.getJobType() == UsageJobVO.JOB_TYPE_RECURRING) {
                        _usageJobDao.createNewJob(_hostname, _pid, UsageJobVO.JOB_TYPE_RECURRING);
                    }
                    jobUpdateTxn.commit();
                } finally {
                    jobUpdateTxn.close();
                }

                return;
            }
            Date startDate = new Date(startDateMillis);
            Date endDate = new Date(endDateMillis);
            logger.info("Parsing usage records between [{}] and [{}].", DateUtil.displayDateInTimezone(usageAggregationTimeZone, startDate),
                    DateUtil.displayDateInTimezone(usageAggregationTimeZone, endDate));

            List<AccountVO> accounts = null;
            List<UserStatisticsVO> userStats = null;
            Map<String, UsageNetworkVO> networkStats = null;
            List<VmDiskStatisticsVO> vmDiskStats = null;
            Map<String, UsageVmDiskVO> vmDiskUsages = null;
            List<BucketStatisticsVO> bucketStats = null;
            TransactionLegacy userTxn = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
            try {
                Long limit = Long.valueOf(500);
                Long offset = Long.valueOf(0);
                Long lastAccountId = _usageDao.getLastAccountId();
                if (lastAccountId == null) {
                    lastAccountId = Long.valueOf(0);
                }

                do {
                    Filter filter = new Filter(AccountVO.class, "id", true, offset, limit);

                    accounts = _accountDao.findActiveAccounts(lastAccountId, filter);

                    if ((accounts != null) && !accounts.isEmpty()) {
                        // now update the accounts in the cloud_usage db
                        _usageDao.updateAccounts(accounts);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((accounts != null) && !accounts.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                do {
                    Filter filter = new Filter(AccountVO.class, "id", true, offset, limit);

                    accounts = _accountDao.findRecentlyDeletedAccounts(lastAccountId, startDate, filter);

                    if ((accounts != null) && !accounts.isEmpty()) {
                        // now update the accounts in the cloud_usage db
                        _usageDao.updateAccounts(accounts);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((accounts != null) && !accounts.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                do {
                    Filter filter = new Filter(AccountVO.class, "id", true, offset, limit);

                    accounts = _accountDao.findNewAccounts(lastAccountId, filter);

                    if ((accounts != null) && !accounts.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.saveAccounts(accounts);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((accounts != null) && !accounts.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                // get all the user stats to create usage records for the network usage
                Long lastUserStatsId = _usageDao.getLastUserStatsId();
                if (lastUserStatsId == null) {
                    lastUserStatsId = Long.valueOf(0);
                }

                SearchCriteria<UserStatisticsVO> sc2 = _userStatsDao.createSearchCriteria();
                sc2.addAnd("id", SearchCriteria.Op.LTEQ, lastUserStatsId);
                do {
                    Filter filter = new Filter(UserStatisticsVO.class, "id", true, offset, limit);

                    userStats = _userStatsDao.search(sc2, filter);

                    if ((userStats != null) && !userStats.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.updateUserStats(userStats);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((userStats != null) && !userStats.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                sc2 = _userStatsDao.createSearchCriteria();
                sc2.addAnd("id", SearchCriteria.Op.GT, lastUserStatsId);
                do {
                    Filter filter = new Filter(UserStatisticsVO.class, "id", true, offset, limit);

                    userStats = _userStatsDao.search(sc2, filter);

                    if ((userStats != null) && !userStats.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.saveUserStats(userStats);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((userStats != null) && !userStats.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                // get all the vm network stats to create usage_VM_network records for the vm network usage
                Long lastVmDiskStatsId = _usageDao.getLastVmDiskStatsId();
                if (lastVmDiskStatsId == null) {
                    lastVmDiskStatsId = Long.valueOf(0);
                }
                SearchCriteria<VmDiskStatisticsVO> sc4 = _vmDiskStatsDao.createSearchCriteria();
                sc4.addAnd("id", SearchCriteria.Op.LTEQ, lastVmDiskStatsId);
                do {
                    Filter filter = new Filter(VmDiskStatisticsVO.class, "id", true, offset, limit);

                    vmDiskStats = _vmDiskStatsDao.search(sc4, filter);

                    if ((vmDiskStats != null) && !vmDiskStats.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.updateVmDiskStats(vmDiskStats);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((vmDiskStats != null) && !vmDiskStats.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                sc4 = _vmDiskStatsDao.createSearchCriteria();
                sc4.addAnd("id", SearchCriteria.Op.GT, lastVmDiskStatsId);
                do {
                    Filter filter = new Filter(VmDiskStatisticsVO.class, "id", true, offset, limit);

                    vmDiskStats = _vmDiskStatsDao.search(sc4, filter);

                    if ((vmDiskStats != null) && !vmDiskStats.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.saveVmDiskStats(vmDiskStats);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((vmDiskStats != null) && !vmDiskStats.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                // get all the user stats to create usage records for the bucket usage
                Long lastBucketStatsId = _usageDao.getLastBucketStatsId();
                if (lastBucketStatsId == null) {
                    lastBucketStatsId = Long.valueOf(0);
                }

                SearchCriteria<BucketStatisticsVO> sc5 = _bucketStatisticsDao.createSearchCriteria();
                sc5.addAnd("id", SearchCriteria.Op.LTEQ, lastBucketStatsId);
                do {
                    Filter filter = new Filter(BucketStatisticsVO.class, "id", true, offset, limit);

                    bucketStats = _bucketStatisticsDao.search(sc5, filter);

                    if ((bucketStats != null) && !bucketStats.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.updateBucketStats(bucketStats);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((bucketStats != null) && !bucketStats.isEmpty());

                // reset offset
                offset = Long.valueOf(0);

                sc5 = _bucketStatisticsDao.createSearchCriteria();
                sc5.addAnd("id", SearchCriteria.Op.GT, lastBucketStatsId);
                do {
                    Filter filter = new Filter(BucketStatisticsVO.class, "id", true, offset, limit);

                    bucketStats = _bucketStatisticsDao.search(sc5, filter);

                    if ((bucketStats != null) && !bucketStats.isEmpty()) {
                        // now copy the accounts to cloud_usage db
                        _usageDao.saveBucketStats(bucketStats);
                    }
                    offset = new Long(offset.longValue() + limit.longValue());
                } while ((bucketStats != null) && !bucketStats.isEmpty());

            } finally {
                userTxn.close();
            }

            // TODO:  Fetch a maximum number of events and process them before moving on to the next range of events

            // - get a list of the latest events
            // - insert the latest events into the usage.events table
            List<UsageEventVO> events = _usageEventDao.getRecentEvents(new Date(endDateMillis));

            TransactionLegacy usageTxn = TransactionLegacy.open(TransactionLegacy.USAGE_DB);
            try {
                usageTxn.start();

                // make sure start date is before all of our un-processed events (the events are ordered oldest
                // to newest, so just test against the first event)
                if ((events != null) && (events.size() > 0)) {
                    Date oldestEventDate = events.get(0).getCreateDate();
                    if (oldestEventDate.getTime() < startDateMillis) {
                        startDateMillis = oldestEventDate.getTime();
                        startDate = new Date(startDateMillis);
                    }

                    // - loop over the list of events and create entries in the helper tables
                    // - create the usage records using the parse methods below
                    for (UsageEventVO event : events) {
                        event.setProcessed(true);
                        _usageEventDao.update(event.getId(), event);
                        createHelperRecord(event);
                    }
                }

                // TODO:  Fetch a maximum number of user stats and process them before moving on to the next range of user stats

                // get user stats in order to compute network usage
                networkStats = _usageNetworkDao.getRecentNetworkStats();

                Calendar recentlyDeletedCal = Calendar.getInstance(usageAggregationTimeZone);
                recentlyDeletedCal.setTimeInMillis(startDateMillis);
                recentlyDeletedCal.add(Calendar.MINUTE, -1 * THREE_DAYS_IN_MINUTES);
                Date recentlyDeletedDate = recentlyDeletedCal.getTime();

                // Keep track of user stats for an account, across all of its public IPs
                Map<String, UserStatisticsVO> aggregatedStats = new HashMap<String, UserStatisticsVO>();
                int startIndex = 0;
                do {
                    userStats = _userStatsDao.listActiveAndRecentlyDeleted(recentlyDeletedDate, startIndex, 500);

                    if (userStats != null) {
                        for (UserStatisticsVO userStat : userStats) {
                            if (userStat.getDeviceId() != null) {
                                String hostKey = userStat.getDataCenterId() + "-" + userStat.getAccountId() + "-Host-" + userStat.getDeviceId() + "-Network-" + userStat.getNetworkId();
                                UserStatisticsVO hostAggregatedStat = aggregatedStats.get(hostKey);
                                if (hostAggregatedStat == null) {
                                    hostAggregatedStat =
                                            new UserStatisticsVO(userStat.getAccountId(), userStat.getDataCenterId(), userStat.getPublicIpAddress(), userStat.getDeviceId(),
                                                    userStat.getDeviceType(), userStat.getNetworkId());
                                }

                                hostAggregatedStat.setAggBytesSent(hostAggregatedStat.getAggBytesSent() + userStat.getAggBytesSent());
                                hostAggregatedStat.setAggBytesReceived(hostAggregatedStat.getAggBytesReceived() + userStat.getAggBytesReceived());
                                aggregatedStats.put(hostKey, hostAggregatedStat);
                            }
                        }
                    }
                    startIndex += 500;
                } while ((userStats != null) && !userStats.isEmpty());

                // loop over the user stats, create delta entries in the usage_network helper table
                int numAcctsProcessed = 0;
                usageNetworks.clear();
                for (String key : aggregatedStats.keySet()) {
                    UsageNetworkVO currentNetworkStats = null;
                    if (networkStats != null) {
                        currentNetworkStats = networkStats.get(key);
                    }

                    createNetworkHelperEntry(aggregatedStats.get(key), currentNetworkStats, endDateMillis);
                    numAcctsProcessed++;
                }
                _usageNetworkDao.saveUsageNetworks(usageNetworks);

                if (logger.isDebugEnabled()) {
                    logger.debug("created network stats helper entries for " + numAcctsProcessed + " accts");
                }

                // get vm disk stats in order to compute vm disk usage
                vmDiskUsages = _usageVmDiskDao.getRecentVmDiskStats();

                // Keep track of user stats for an account, across all of its public IPs
                Map<String, VmDiskStatisticsVO> aggregatedDiskStats = new HashMap<String, VmDiskStatisticsVO>();
                startIndex = 0;
                do {
                    vmDiskStats = _vmDiskStatsDao.listActiveAndRecentlyDeleted(recentlyDeletedDate, startIndex, 500);

                    if (vmDiskUsages != null) {
                        for (VmDiskStatisticsVO vmDiskStat : vmDiskStats) {
                            if (vmDiskStat.getVmId() != null) {
                                String hostKey =
                                        vmDiskStat.getDataCenterId() + "-" + vmDiskStat.getAccountId() + "-Vm-" + vmDiskStat.getVmId() + "-Disk-" + vmDiskStat.getVolumeId();
                                VmDiskStatisticsVO hostAggregatedStat = aggregatedDiskStats.get(hostKey);
                                if (hostAggregatedStat == null) {
                                    hostAggregatedStat =
                                            new VmDiskStatisticsVO(vmDiskStat.getAccountId(), vmDiskStat.getDataCenterId(), vmDiskStat.getVmId(), vmDiskStat.getVolumeId());
                                }

                                hostAggregatedStat.setAggIORead(hostAggregatedStat.getAggIORead() + vmDiskStat.getAggIORead());
                                hostAggregatedStat.setAggIOWrite(hostAggregatedStat.getAggIOWrite() + vmDiskStat.getAggIOWrite());
                                hostAggregatedStat.setAggBytesRead(hostAggregatedStat.getAggBytesRead() + vmDiskStat.getAggBytesRead());
                                hostAggregatedStat.setAggBytesWrite(hostAggregatedStat.getAggBytesWrite() + vmDiskStat.getAggBytesWrite());
                                aggregatedDiskStats.put(hostKey, hostAggregatedStat);
                            }
                        }
                    }
                    startIndex += 500;
                } while ((userStats != null) && !userStats.isEmpty());

                // loop over the user stats, create delta entries in the usage_disk helper table
                numAcctsProcessed = 0;
                usageVmDisks.clear();
                for (String key : aggregatedDiskStats.keySet()) {
                    UsageVmDiskVO currentVmDiskStats = null;
                    if (vmDiskStats != null) {
                        currentVmDiskStats = vmDiskUsages.get(key);
                    }

                    createVmDiskHelperEntry(aggregatedDiskStats.get(key), currentVmDiskStats, endDateMillis);
                    numAcctsProcessed++;
                }
                _usageVmDiskDao.saveUsageVmDisks(usageVmDisks);

                if (logger.isDebugEnabled()) {
                    logger.debug("created vm disk stats helper entries for " + numAcctsProcessed + " accts");
                }

                // commit the helper records, then start a new transaction
                usageTxn.commit();
                usageTxn.start();

                boolean parsed = false;
                numAcctsProcessed = 0;

                Date currentStartDate = startDate;
                Date currentEndDate = endDate;
                Date tempDate = endDate;

                Calendar aggregateCal = Calendar.getInstance(usageAggregationTimeZone);

                while ((tempDate.after(startDate)) && ((tempDate.getTime() - startDate.getTime()) > 60000)) {
                    currentEndDate = tempDate;
                    aggregateCal.setTime(tempDate);
                    aggregateCal.add(Calendar.MINUTE, -_aggregationDuration);
                    tempDate = aggregateCal.getTime();
                }

                while (!currentEndDate.after(endDate) || (currentEndDate.getTime() - endDate.getTime() < 60000)) {
                    Long offset = Long.valueOf(0);
                    Long limit = Long.valueOf(500);

                    do {
                        Filter filter = new Filter(AccountVO.class, "id", true, offset, limit);
                        accounts = _accountDao.listAll(filter);
                        if ((accounts != null) && !accounts.isEmpty()) {
                            for (AccountVO account : accounts) {
                                parsed = parseHelperTables(account, currentStartDate, currentEndDate);
                                numAcctsProcessed++;
                            }
                        }
                        offset = new Long(offset.longValue() + limit.longValue());
                    } while ((accounts != null) && !accounts.isEmpty());

                    if (logger.isDebugEnabled()) {
                        logger.debug("processed VM/Network Usage for " + numAcctsProcessed + " ACTIVE accts");
                    }
                    numAcctsProcessed = 0;

                    // reset offset
                    offset = Long.valueOf(0);

                    do {
                        Filter filter = new Filter(AccountVO.class, "id", true, offset, limit);

                        accounts = _accountDao.findRecentlyDeletedAccounts(null, recentlyDeletedDate, filter);

                        if ((accounts != null) && !accounts.isEmpty()) {
                            for (AccountVO account : accounts) {
                                parsed = parseHelperTables(account, currentStartDate, currentEndDate);
                                List<Long> publicTemplates = _usageDao.listPublicTemplatesByAccount(account.getId());
                                for (Long templateId : publicTemplates) {
                                    //mark public templates owned by deleted accounts as deleted
                                    List<UsageStorageVO> storageVOs = _usageStorageDao.listById(account.getId(), templateId, StorageTypes.TEMPLATE);
                                    if (storageVOs.size() > 1) {
                                        logger.warn("More that one usage entry for storage: " + templateId + " assigned to account: " + account.getId() +
                                                "; marking them all as deleted...");
                                    }
                                    for (UsageStorageVO storageVO : storageVOs) {
                                        if (logger.isDebugEnabled()) {
                                            logger.debug("deleting template: " + storageVO.getId() + " from account: " + storageVO.getAccountId());
                                        }
                                        storageVO.setDeleted(account.getRemoved());
                                        _usageStorageDao.update(storageVO);
                                    }
                                }
                                numAcctsProcessed++;
                            }
                        }
                        offset = new Long(offset.longValue() + limit.longValue());
                    } while ((accounts != null) && !accounts.isEmpty());

                    currentStartDate = new Date(currentEndDate.getTime() + 1);
                    aggregateCal.setTime(currentEndDate);
                    aggregateCal.add(Calendar.MINUTE, _aggregationDuration);
                    currentEndDate = aggregateCal.getTime();
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("processed Usage for " + numAcctsProcessed + " RECENTLY DELETED accts");
                }

                // FIXME: we don't break the above loop if something fails to parse, so it gets reset every account,
                //        do we want to break out of processing accounts and rollback if there are errors?
                if (!parsed) {
                    usageTxn.rollback();
                } else {
                    success = true;
                }
            } catch (Exception ex) {
                logger.error("Exception in usage manager", ex);
                usageTxn.rollback();
            } finally {
                // everything seemed to work...set endDate as the last success date
                _usageJobDao.updateJobSuccess(job.getId(), startDateMillis, endDateMillis, System.currentTimeMillis() - timeStart, success);

                // create a new job if this is a recurring job
                if (job.getJobType() == UsageJobVO.JOB_TYPE_RECURRING) {
                    _usageJobDao.createNewJob(_hostname, _pid, UsageJobVO.JOB_TYPE_RECURRING);
                }
                usageTxn.commit();
                usageTxn.close();

                // switch back to CLOUD_DB
                TransactionLegacy swap = TransactionLegacy.open(TransactionLegacy.CLOUD_DB);
                if (!success) {
                    _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_USAGE_SERVER_RESULT, 0, new Long(0), "Usage job failed. Job id: " + job.getId(),
                            "Usage job failed. Job id: " + job.getId());
                } else {
                    _alertMgr.clearAlert(AlertManager.AlertType.ALERT_TYPE_USAGE_SERVER_RESULT, 0, 0);
                }
                swap.close();

            }
        } catch (Exception e) {
            logger.error("Usage Manager error", e);
        }
    }

    private boolean parseHelperTables(AccountVO account, Date currentStartDate, Date currentEndDate) {
        boolean parsed = false;

        for (UsageParser parser : usageParsers) {
            parsed = parser.doParsing(account, currentStartDate, currentEndDate);

            logger.debug("{} usage was {} parsed for [{}].", parser.getParserName(), parsed ? "successfully" : "not successfully", account);
        }

        return parsed;
    }

    private void createHelperRecord(UsageEventVO event) {
        String eventType = event.getType();
        try {
            if (isVMEvent(eventType)) {
                createVMHelperEvent(event);
            } else if (isIPEvent(eventType)) {
                createIPHelperEvent(event);
            } else if (isVolumeEvent(eventType)) {
                createVolumeHelperEvent(event);
            } else if (isTemplateEvent(eventType)) {
                createTemplateHelperEvent(event);
            } else if (isISOEvent(eventType)) {
                createISOHelperEvent(event);
            } else if (isSnapshotEvent(eventType)) {
                createSnapshotHelperEvent(event);
            } else if (isLoadBalancerEvent(eventType)) {
                createLoadBalancerHelperEvent(event);
            } else if (isPortForwardingEvent(eventType)) {
                createPortForwardingHelperEvent(event);
            } else if (isNetworkOfferingEvent(eventType)) {
                createNetworkOfferingEvent(event);
            } else if (isVPNUserEvent(eventType)) {
                handleVpnUserEvent(event);
            } else if (isSecurityGroupEvent(eventType)) {
                createSecurityGroupEvent(event);
            } else if (isVmSnapshotEvent(eventType)) {
                handleVMSnapshotEvent(event);
            } else if (isVmSnapshotOnPrimaryEvent(eventType)) {
                createVmSnapshotOnPrimaryEvent(event);
            } else if (isBackupEvent(eventType)) {
                createBackupEvent(event);
            } else if (EventTypes.isNetworkEvent(eventType)) {
                handleNetworkEvent(event);
            } else if (EventTypes.isVpcEvent(eventType)) {
                handleVpcEvent(event);
            }
        } catch (EntityExistsException e) {
            logger.warn(String.format("Failed to create usage event id: %d type: %s due to %s", event.getId(), eventType, e.getMessage()), e);
        }
    }

    private boolean isVMEvent(String eventType) {
        if (eventType == null)
            return false;
        return eventType.startsWith("VM.");
    }

    private boolean isIPEvent(String eventType) {
        if (eventType == null)
            return false;
        return eventType.startsWith("NET.IP");
    }

    private boolean isVolumeEvent(String eventType) {
        return eventType != null &&
                (eventType.equals(EventTypes.EVENT_VOLUME_CREATE) || eventType.equals(EventTypes.EVENT_VOLUME_DELETE) || eventType.equals(EventTypes.EVENT_VOLUME_RESIZE) || eventType.equals(EventTypes.EVENT_VOLUME_UPLOAD));
    }

    private boolean isTemplateEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_TEMPLATE_CREATE) || eventType.equals(EventTypes.EVENT_TEMPLATE_COPY) || eventType.equals(EventTypes.EVENT_TEMPLATE_DELETE));
    }

    private boolean isISOEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_ISO_CREATE) || eventType.equals(EventTypes.EVENT_ISO_COPY) || eventType.equals(EventTypes.EVENT_ISO_DELETE));
    }

    private boolean isSnapshotEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_SNAPSHOT_CREATE) || eventType.equals(EventTypes.EVENT_SNAPSHOT_DELETE));
    }

    private boolean isLoadBalancerEvent(String eventType) {
        if (eventType == null)
            return false;
        return eventType.startsWith("LB.");
    }

    private boolean isPortForwardingEvent(String eventType) {
        if (eventType == null)
            return false;
        return eventType.startsWith("NET.RULE");
    }

    private boolean isNetworkOfferingEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_NETWORK_OFFERING_CREATE) || eventType.equals(EventTypes.EVENT_NETWORK_OFFERING_DELETE) ||
                eventType.equals(EventTypes.EVENT_NETWORK_OFFERING_ASSIGN) || eventType.equals(EventTypes.EVENT_NETWORK_OFFERING_REMOVE));
    }

    private boolean isVPNUserEvent(String eventType) {
        if (eventType == null)
            return false;
        return eventType.startsWith("VPN.USER");
    }

    private boolean isSecurityGroupEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_SECURITY_GROUP_ASSIGN) || eventType.equals(EventTypes.EVENT_SECURITY_GROUP_REMOVE));
    }

    private boolean isVmSnapshotEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_VM_SNAPSHOT_CREATE) || eventType.equals(EventTypes.EVENT_VM_SNAPSHOT_DELETE));
    }

    private boolean isVmSnapshotOnPrimaryEvent(String eventType) {
        if (eventType == null)
            return false;
        return (eventType.equals(EventTypes.EVENT_VM_SNAPSHOT_ON_PRIMARY) || eventType.equals(EventTypes.EVENT_VM_SNAPSHOT_OFF_PRIMARY));
    }

    private boolean isBackupEvent(String eventType) {
        return eventType != null && (
                eventType.equals(EventTypes.EVENT_VM_BACKUP_OFFERING_ASSIGN) ||
                eventType.equals(EventTypes.EVENT_VM_BACKUP_OFFERING_REMOVED_AND_BACKUPS_DELETED) ||
                eventType.equals(EventTypes.EVENT_VM_BACKUP_USAGE_METRIC));
    }

    private void createVMHelperEvent(UsageEventVO event) {

        // One record for handling VM.START and VM.STOP
        // One record for handling VM.CREATE and VM.DESTROY
        // VM events have the parameter "id=<virtualMachineId>"
        long vmId = event.getResourceId();
        Long soId = event.getOfferingId();
        ; // service offering id
        long zoneId = event.getZoneId();
        String vmName = event.getResourceName();

        if (EventTypes.EVENT_VM_START.equals(event.getType())) {
            // create a new usage_VM_instance row for this VM
            try {

                SearchCriteria<UsageVMInstanceVO> sc = _usageInstanceDao.createSearchCriteria();
                sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
                sc.addAnd("endDate", SearchCriteria.Op.NULL);
                sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.RUNNING_VM);
                List<UsageVMInstanceVO> usageInstances = _usageInstanceDao.search(sc, null);
                if (usageInstances != null) {
                    if (usageInstances.size() > 0) {
                        logger.error("found entries for a vm running with id: " + vmId + ", which are not stopped. Ending them all...");
                        for (UsageVMInstanceVO usageInstance : usageInstances) {
                            usageInstance.setEndDate(event.getCreateDate());
                            _usageInstanceDao.update(usageInstance);
                        }
                    }
                }

                sc = _usageInstanceDao.createSearchCriteria();
                sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
                sc.addAnd("endDate", SearchCriteria.Op.NULL);
                sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.ALLOCATED_VM);
                usageInstances = _usageInstanceDao.search(sc, null);
                if (usageInstances == null || (usageInstances.size() == 0)) {
                    logger.error("Cannot find allocated vm entry for a vm running with id: " + vmId);
                } else if (usageInstances.size() == 1) {
                    UsageVMInstanceVO usageInstance = usageInstances.get(0);
                    if (usageInstance.getSerivceOfferingId() != soId) {
                        //Service Offering changed after Vm creation
                        //End current Allocated usage and create new Allocated Vm entry with new soId
                        usageInstance.setEndDate(event.getCreateDate());
                        _usageInstanceDao.update(usageInstance);
                        usageInstance.setServiceOfferingId(soId);
                        usageInstance.setStartDate(event.getCreateDate());
                        usageInstance.setEndDate(null);
                        populateDynamicComputeOfferingDetailsAndPersist(usageInstance, event.getId());
                    }
                }

                Long templateId = event.getTemplateId();
                String hypervisorType = event.getResourceType();

                // add this VM to the usage helper table
                UsageVMInstanceVO usageInstanceNew =
                        new UsageVMInstanceVO(UsageTypes.RUNNING_VM, zoneId, event.getAccountId(), vmId, vmName, soId, templateId, hypervisorType, event.getCreateDate(),
                                null);
                populateDynamicComputeOfferingDetailsAndPersist(usageInstanceNew, event.getId());
            } catch (Exception ex) {
                logger.error("Error saving usage instance for vm: " + vmId, ex);
            }
        } else if (EventTypes.EVENT_VM_STOP.equals(event.getType())) {
            // find the latest usage_VM_instance row, update the stop date (should be null) to the event date
            // FIXME: search criteria needs to have some kind of type information so we distinguish between START/STOP and CREATE/DESTROY
            SearchCriteria<UsageVMInstanceVO> sc = _usageInstanceDao.createSearchCriteria();
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
            sc.addAnd("endDate", SearchCriteria.Op.NULL);
            sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.RUNNING_VM);
            List<UsageVMInstanceVO> usageInstances = _usageInstanceDao.search(sc, null);
            if (usageInstances != null) {
                if (usageInstances.size() > 1) {
                    logger.warn("found multiple entries for a vm running with id: " + vmId + ", ending them all...");
                }
                for (UsageVMInstanceVO usageInstance : usageInstances) {
                    usageInstance.setEndDate(event.getCreateDate());
                    // TODO: UsageVMInstanceVO should have an ID field and we should do updates through that field since we are really
                    //       updating one row at a time here
                    _usageInstanceDao.update(usageInstance);
                }
            }
        } else if (EventTypes.EVENT_VM_CREATE.equals(event.getType())) {
            try {
                Long templateId = event.getTemplateId();
                String hypervisorType = event.getResourceType();

                // add this VM to the usage helper table
                UsageVMInstanceVO usageInstanceNew = new UsageVMInstanceVO(UsageTypes.ALLOCATED_VM, zoneId, event.getAccountId(), vmId, vmName,
                        soId, templateId, hypervisorType, event.getCreateDate(), null);
                populateDynamicComputeOfferingDetailsAndPersist(usageInstanceNew, event.getId());
            } catch (Exception ex) {
                logger.error("Error saving usage instance for vm: " + vmId, ex);
            }
        } else if (EventTypes.EVENT_VM_DESTROY.equals(event.getType())) {
            SearchCriteria<UsageVMInstanceVO> sc = _usageInstanceDao.createSearchCriteria();
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
            sc.addAnd("endDate", SearchCriteria.Op.NULL);
            sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.ALLOCATED_VM);
            List<UsageVMInstanceVO> usageInstances = _usageInstanceDao.search(sc, null);
            if (usageInstances != null) {
                if (usageInstances.size() > 1) {
                    logger.warn("found multiple entries for a vm allocated with id: " + vmId + ", detroying them all...");
                }
                for (UsageVMInstanceVO usageInstance : usageInstances) {
                    usageInstance.setEndDate(event.getCreateDate());
                    _usageInstanceDao.update(usageInstance);
                }
            }
        } else if (EventTypes.EVENT_VM_UPGRADE.equals(event.getType())) {
            SearchCriteria<UsageVMInstanceVO> sc = _usageInstanceDao.createSearchCriteria();
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
            sc.addAnd("endDate", SearchCriteria.Op.NULL);
            sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.ALLOCATED_VM);
            List<UsageVMInstanceVO> usageInstances = _usageInstanceDao.search(sc, null);
            if (usageInstances != null) {
                if (usageInstances.size() > 1) {
                    logger.warn("found multiple entries for a vm allocated with id: " + vmId + ", updating end_date for all of them...");
                }
                for (UsageVMInstanceVO usageInstance : usageInstances) {
                    usageInstance.setEndDate(event.getCreateDate());
                    _usageInstanceDao.update(usageInstance);
                }
            }

            Long templateId = event.getTemplateId();
            String hypervisorType = event.getResourceType();
            // add this VM to the usage helper table
            UsageVMInstanceVO usageInstanceNew =
                    new UsageVMInstanceVO(UsageTypes.ALLOCATED_VM, zoneId, event.getAccountId(), vmId, vmName, soId, templateId, hypervisorType, event.getCreateDate(), null);
            populateDynamicComputeOfferingDetailsAndPersist(usageInstanceNew, event.getId());
        } else if (EventTypes.EVENT_VM_DYNAMIC_SCALE.equals(event.getType())) {
            // Ending the running vm event
            SearchCriteria<UsageVMInstanceVO> sc = _usageInstanceDao.createSearchCriteria();
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
            sc.addAnd("endDate", SearchCriteria.Op.NULL);
            sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.RUNNING_VM);
            List<UsageVMInstanceVO> usageInstances = _usageInstanceDao.search(sc, null);
            if (usageInstances != null) {
                if (usageInstances.size() > 1) {
                    logger.warn("found multiple entries for a vm running with id: " + vmId + ", ending them all...");
                }
                for (UsageVMInstanceVO usageInstance : usageInstances) {
                    usageInstance.setEndDate(event.getCreateDate());
                    _usageInstanceDao.update(usageInstance);
                }
            }

            sc = _usageInstanceDao.createSearchCriteria();
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, Long.valueOf(vmId));
            sc.addAnd("endDate", SearchCriteria.Op.NULL);
            sc.addAnd("usageType", SearchCriteria.Op.EQ, UsageTypes.ALLOCATED_VM);
            usageInstances = _usageInstanceDao.search(sc, null);
            if (usageInstances == null || (usageInstances.size() == 0)) {
                logger.error("Cannot find allocated vm entry for a vm running with id: " + vmId);
            } else if (usageInstances.size() == 1) {
                UsageVMInstanceVO usageInstance = usageInstances.get(0);
                if (usageInstance.getSerivceOfferingId() != soId) {
                    //Service Offering changed after Vm creation
                    //End current Allocated usage and create new Allocated Vm entry with new soId
                    usageInstance.setEndDate(event.getCreateDate());
                    _usageInstanceDao.update(usageInstance);
                    usageInstance.setServiceOfferingId(soId);
                    usageInstance.setStartDate(event.getCreateDate());
                    usageInstance.setEndDate(null);
                    populateDynamicComputeOfferingDetailsAndPersist(usageInstance, event.getId());
                }
            }

            Long templateId = event.getTemplateId();
            String hypervisorType = event.getResourceType();

            // add this VM to the usage helper table with new service offering Id
            UsageVMInstanceVO usageInstanceNew =
                    new UsageVMInstanceVO(UsageTypes.RUNNING_VM, zoneId, event.getAccountId(), vmId, vmName, soId, templateId, hypervisorType, event.getCreateDate(), null);
            populateDynamicComputeOfferingDetailsAndPersist(usageInstanceNew, event.getId());
        }
    }

    private void populateDynamicComputeOfferingDetailsAndPersist(UsageVMInstanceVO usageInstance, Long eventId) {

        //populate the cpu, memory and cpuSpeed of the vm when created from a dynamic offering.
        UsageEventDetailsVO cpuNumber = _usageEventDetailsDao.findDetail(eventId, UsageEventVO.DynamicParameters.cpuNumber.name());
        if (cpuNumber != null) {
            usageInstance.setCpuCores(Long.parseLong(cpuNumber.getValue()));
        } else {
            usageInstance.setCpuCores(null);
        }

        UsageEventDetailsVO cpuSpeed = _usageEventDetailsDao.findDetail(eventId, UsageEventVO.DynamicParameters.cpuSpeed.name());
        if (cpuSpeed != null) {
            usageInstance.setCpuSpeed(Long.parseLong(cpuSpeed.getValue()));
        } else {
            usageInstance.setCpuSpeed(null);
        }

        UsageEventDetailsVO memory = _usageEventDetailsDao.findDetail(eventId, UsageEventVO.DynamicParameters.memory.name());
        if (memory != null) {
            usageInstance.setMemory(Long.parseLong(memory.getValue()));
        } else {
            usageInstance.setMemory(null);
        }
        _usageInstanceDao.persist(usageInstance);
    }

    private void createNetworkHelperEntry(UserStatisticsVO userStat, UsageNetworkVO usageNetworkStats, long timestamp) {
        long currentAccountedBytesSent = 0L;
        long currentAccountedBytesReceived = 0L;
        if (usageNetworkStats != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("getting current accounted bytes for... accountId: " + usageNetworkStats.getAccountId() + " in zone: " + userStat.getDataCenterId() +
                        "; abr: " + toHumanReadableSize(usageNetworkStats.getAggBytesReceived()) + "; abs: " + toHumanReadableSize(usageNetworkStats.getAggBytesSent()));
            }
            currentAccountedBytesSent = usageNetworkStats.getAggBytesSent();
            currentAccountedBytesReceived = usageNetworkStats.getAggBytesReceived();
        }
        long bytesSent = userStat.getAggBytesSent() - currentAccountedBytesSent;
        long bytesReceived = userStat.getAggBytesReceived() - currentAccountedBytesReceived;

        if (bytesSent < 0) {
            logger.warn("Calculated negative value for bytes sent: " + toHumanReadableSize(bytesSent) + ", user stats say: " + toHumanReadableSize(userStat.getAggBytesSent()) +
                    ", previous network usage was: " + toHumanReadableSize(currentAccountedBytesSent));
            bytesSent = 0;
        }
        if (bytesReceived < 0) {
            logger.warn("Calculated negative value for bytes received: " + toHumanReadableSize(bytesReceived) + ", user stats say: " + toHumanReadableSize(userStat.getAggBytesReceived()) +
                    ", previous network usage was: " + toHumanReadableSize(currentAccountedBytesReceived));
            bytesReceived = 0;
        }

        long hostId = 0;

        if (userStat.getDeviceId() != null) {
            hostId = userStat.getDeviceId();
        }

        UsageNetworkVO usageNetworkVO =
                new UsageNetworkVO(userStat.getAccountId(), userStat.getDataCenterId(), hostId, userStat.getDeviceType(), userStat.getNetworkId(), bytesSent, bytesReceived,
                        userStat.getAggBytesReceived(), userStat.getAggBytesSent(), timestamp);
        if (logger.isDebugEnabled()) {
            logger.debug("creating networkHelperEntry... accountId: " + userStat.getAccountId() + " in zone: " + userStat.getDataCenterId() + "; abr: " +
                    userStat.getAggBytesReceived() + "; abs: " + userStat.getAggBytesSent() + "; curABS: " + currentAccountedBytesSent + "; curABR: " +
                    currentAccountedBytesReceived + "; ubs: " + bytesSent + "; ubr: " + bytesReceived);
        }
        usageNetworks.add(usageNetworkVO);
    }

    private void createVmDiskHelperEntry(VmDiskStatisticsVO vmDiskStat, UsageVmDiskVO usageVmDiskStat, long timestamp) {
        long currentAccountedIORead = 0L;
        long currentAccountedIOWrite = 0L;
        long currentAccountedBytesRead = 0L;
        long currentAccountedBytesWrite = 0L;
        if (usageVmDiskStat != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("getting current accounted bytes for... accountId: " + usageVmDiskStat.getAccountId() + " in zone: " + vmDiskStat.getDataCenterId() +
                        "; aiw: " + toHumanReadableSize(vmDiskStat.getAggIOWrite()) + "; air: " + toHumanReadableSize(usageVmDiskStat.getAggIORead()) + "; abw: " + toHumanReadableSize(vmDiskStat.getAggBytesWrite()) + "; abr: " +
                        toHumanReadableSize(usageVmDiskStat.getAggBytesRead()));
            }
            currentAccountedIORead = usageVmDiskStat.getAggIORead();
            currentAccountedIOWrite = usageVmDiskStat.getAggIOWrite();
            currentAccountedBytesRead = usageVmDiskStat.getAggBytesRead();
            currentAccountedBytesWrite = usageVmDiskStat.getAggBytesWrite();
        }
        long ioRead = vmDiskStat.getAggIORead() - currentAccountedIORead;
        long ioWrite = vmDiskStat.getAggIOWrite() - currentAccountedIOWrite;
        long bytesRead = vmDiskStat.getAggBytesRead() - currentAccountedBytesRead;
        long bytesWrite = vmDiskStat.getAggBytesWrite() - currentAccountedBytesWrite;

        if (ioRead < 0) {
            logger.warn("Calculated negative value for io read: " + toHumanReadableSize(ioRead) + ", vm disk stats say: " + toHumanReadableSize(vmDiskStat.getAggIORead()) + ", previous vm disk usage was: " +
                    toHumanReadableSize(currentAccountedIORead));
            ioRead = 0;
        }
        if (ioWrite < 0) {
            logger.warn("Calculated negative value for io write: " + toHumanReadableSize(ioWrite) + ", vm disk stats say: " + toHumanReadableSize(vmDiskStat.getAggIOWrite()) + ", previous vm disk usage was: " +
                    toHumanReadableSize(currentAccountedIOWrite));
            ioWrite = 0;
        }
        if (bytesRead < 0) {
            logger.warn("Calculated negative value for bytes read: " + toHumanReadableSize(bytesRead) + ", vm disk stats say: " + toHumanReadableSize(vmDiskStat.getAggBytesRead()) +
                    ", previous vm disk usage was: " + toHumanReadableSize(currentAccountedBytesRead));
            bytesRead = 0;
        }
        if (bytesWrite < 0) {
            logger.warn("Calculated negative value for bytes write: " + toHumanReadableSize(bytesWrite) + ", vm disk stats say: " + toHumanReadableSize(vmDiskStat.getAggBytesWrite()) +
                    ", previous vm disk usage was: " + toHumanReadableSize(currentAccountedBytesWrite));
            bytesWrite = 0;
        }

        long vmId = 0;

        if (vmDiskStat.getVmId() != null) {
            vmId = vmDiskStat.getVmId();
        }

        UsageVmDiskVO usageVmDiskVO =
                new UsageVmDiskVO(vmDiskStat.getAccountId(), vmDiskStat.getDataCenterId(), vmId, vmDiskStat.getVolumeId(), ioRead, ioWrite, vmDiskStat.getAggIORead(),
                        vmDiskStat.getAggIOWrite(), bytesRead, bytesWrite, vmDiskStat.getAggBytesRead(), vmDiskStat.getAggBytesWrite(), timestamp);
        if (logger.isDebugEnabled()) {
            logger.debug("creating vmDiskHelperEntry... accountId: " + vmDiskStat.getAccountId() + " in zone: " + vmDiskStat.getDataCenterId() + "; aiw: " +
                    toHumanReadableSize(vmDiskStat.getAggIOWrite()) + "; air: " + toHumanReadableSize(vmDiskStat.getAggIORead()) + "; curAIR: " + toHumanReadableSize(currentAccountedIORead) + "; curAIW: " + toHumanReadableSize(currentAccountedIOWrite) +
                    "; uir: " + toHumanReadableSize(ioRead) + "; uiw: " + toHumanReadableSize(ioWrite) + "; abw: " + toHumanReadableSize(vmDiskStat.getAggBytesWrite()) + "; abr: " + toHumanReadableSize(vmDiskStat.getAggBytesRead()) + "; curABR: " +
                    toHumanReadableSize(currentAccountedBytesRead) + "; curABW: " + toHumanReadableSize(currentAccountedBytesWrite) + "; ubr: " + toHumanReadableSize(bytesRead) + "; ubw: " + toHumanReadableSize(bytesWrite));
        }
        usageVmDisks.add(usageVmDiskVO);
    }

    private void createIPHelperEvent(UsageEventVO event) {

        String ipAddress = event.getResourceName();

        if (EventTypes.EVENT_NET_IP_ASSIGN.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("assigning ip address: " + ipAddress + " to account: " + event.getAccountId());
            }
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            long zoneId = event.getZoneId();
            long id = event.getResourceId();
            long sourceNat = event.getSize();
            boolean isSourceNat = (sourceNat == 1) ? true : false;
            boolean isSystem = (event.getTemplateId() == null || event.getTemplateId() == 0) ? false : true;
            final UsageEventDetailsVO hiddenDetail = _usageEventDetailsDao.findDetail(event.getId(), "hidden");
            final boolean isHidden = hiddenDetail != null && "true".equals(hiddenDetail.getValue());
            UsageIPAddressVO ipAddressVO =
                    new UsageIPAddressVO(id, event.getAccountId(), acct.getDomainId(), zoneId, ipAddress, isSourceNat, isSystem, event.getCreateDate(), null, isHidden);
            _usageIPAddressDao.persist(ipAddressVO);
        } else if (EventTypes.EVENT_NET_IP_RELEASE.equals(event.getType())) {
            SearchCriteria<UsageIPAddressVO> sc = _usageIPAddressDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("address", SearchCriteria.Op.EQ, ipAddress);
            sc.addAnd("released", SearchCriteria.Op.NULL);
            List<UsageIPAddressVO> ipAddressVOs = _usageIPAddressDao.search(sc, null);
            if (ipAddressVOs.size() > 1) {
                logger.warn("More that one usage entry for ip address: " + ipAddress + " assigned to account: " + event.getAccountId() +
                        "; marking them all as released...");
            }
            for (UsageIPAddressVO ipAddressVO : ipAddressVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("releasing ip address: " + ipAddressVO.getAddress() + " from account: " + ipAddressVO.getAccountId());
                }
                ipAddressVO.setReleased(event.getCreateDate()); // there really shouldn't be more than one
                _usageIPAddressDao.update(ipAddressVO);
            }
        }
    }

    private void createVolumeHelperEvent(UsageEventVO event) {

        long volId = event.getResourceId();

        if (EventTypes.EVENT_VOLUME_CREATE.equals(event.getType())) {
            //For volumes which are 'attached' successfully, set the 'deleted' column in the usage_storage table,
            //so that the secondary storage should stop accounting and only primary will be accounted.
            SearchCriteria<UsageStorageVO> sc = _usageStorageDao.createSearchCriteria();
            sc.addAnd("entityId", SearchCriteria.Op.EQ, volId);
            sc.addAnd("storageType", SearchCriteria.Op.EQ, StorageTypes.VOLUME);
            List<UsageStorageVO> volumesVOs = _usageStorageDao.search(sc, null);
            if (volumesVOs != null) {
                if (volumesVOs.size() == 1) {
                    logger.debug("Setting the volume with id: " + volId + " to 'deleted' in the usage_storage table.");
                    volumesVOs.get(0).setDeleted(event.getCreateDate());
                    _usageStorageDao.update(volumesVOs.get(0));
                }
            }
        }
        if (EventTypes.EVENT_VOLUME_CREATE.equals(event.getType()) || EventTypes.EVENT_VOLUME_RESIZE.equals(event.getType())) {
            SearchCriteria<UsageVolumeVO> sc = _usageVolumeDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("volumeId", SearchCriteria.Op.EQ, volId);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsageVolumeVO> volumesVOs = _usageVolumeDao.search(sc, null);
            if (volumesVOs.size() > 0) {
                //This is a safeguard to avoid double counting of volumes.
                logger.error("Found duplicate usage entry for volume: " + volId + " assigned to account: " + event.getAccountId() + "; marking as deleted...");
            }
            //an entry exists if it is a resize volume event. marking the existing deleted and creating a new one in the case of resize.
            for (UsageVolumeVO volumesVO : volumesVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting volume: " + volumesVO.getId() + " from account: " + volumesVO.getAccountId());
                }
                volumesVO.setDeleted(event.getCreateDate());
                _usageVolumeDao.update(volumesVO);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("create volume with id : " + volId + " for account: " + event.getAccountId());
            }
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageVolumeVO volumeVO = new UsageVolumeVO(volId, event.getZoneId(), event.getAccountId(), acct.getDomainId(), event.getOfferingId(), event.getTemplateId(), event.getSize(), event.getCreateDate(), null);
            _usageVolumeDao.persist(volumeVO);
        } else if (EventTypes.EVENT_VOLUME_DELETE.equals(event.getType())) {
            SearchCriteria<UsageVolumeVO> sc = _usageVolumeDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("volumeId", SearchCriteria.Op.EQ, volId);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsageVolumeVO> volumesVOs = _usageVolumeDao.search(sc, null);
            if (volumesVOs.size() > 1) {
                logger.warn("More that one usage entry for volume: " + volId + " assigned to account: " + event.getAccountId() + "; marking them all as deleted...");
            }
            for (UsageVolumeVO volumesVO : volumesVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting volume: " + volumesVO.getId() + " from account: " + volumesVO.getAccountId());
                }
                volumesVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageVolumeDao.update(volumesVO);
            }
        } else if (EventTypes.EVENT_VOLUME_UPLOAD.equals(event.getType())) {
            //For Upload event add an entry to the usage_storage table.
            SearchCriteria<UsageStorageVO> sc = _usageStorageDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("entityId", SearchCriteria.Op.EQ, volId);
            sc.addAnd("storageType", SearchCriteria.Op.EQ, StorageTypes.VOLUME);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsageStorageVO> volumesVOs = _usageStorageDao.search(sc, null);

            if (volumesVOs.size() > 0) {
                //This is a safeguard to avoid double counting of volumes.
                logger.error("Found duplicate usage entry for volume: " + volId + " assigned to account: " + event.getAccountId() + "; marking as deleted...");
            }
            for (UsageStorageVO volumesVO : volumesVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting volume: " + volumesVO.getId() + " from account: " + volumesVO.getAccountId());
                }
                volumesVO.setDeleted(event.getCreateDate());
                _usageStorageDao.update(volumesVO);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("create volume with id : " + volId + " for account: " + event.getAccountId());
            }
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageStorageVO volumeVO = new UsageStorageVO(volId, event.getZoneId(), event.getAccountId(), acct.getDomainId(), StorageTypes.VOLUME, event.getTemplateId(), event.getSize(), event.getCreateDate(), null);
            _usageStorageDao.persist(volumeVO);
        }
    }

    private void createTemplateHelperEvent(UsageEventVO event) {

        long templateId = -1L;
        long zoneId = -1L;
        long templateSize = -1L;

        templateId = event.getResourceId();
        zoneId = event.getZoneId();
        if (EventTypes.EVENT_TEMPLATE_CREATE.equals(event.getType()) || EventTypes.EVENT_TEMPLATE_COPY.equals(event.getType())) {
            templateSize = event.getSize();
            if (templateSize < 1) {
                logger.error("Incorrect size for template with Id " + templateId);
                return;
            }
            if (zoneId == -1L) {
                logger.error("Incorrect zoneId for template with Id " + templateId);
                return;
            }
        }

        if (EventTypes.EVENT_TEMPLATE_CREATE.equals(event.getType()) || EventTypes.EVENT_TEMPLATE_COPY.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("create template with id : " + templateId + " for account: " + event.getAccountId());
            }
            List<UsageStorageVO> storageVOs = _usageStorageDao.listByIdAndZone(event.getAccountId(), templateId, StorageTypes.TEMPLATE, zoneId);
            if (storageVOs.size() > 0) {
                logger.warn("Usage entry for Template: " + templateId + " assigned to account: " + event.getAccountId() + "already exists in zone " + zoneId);
                return;
            }
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageStorageVO storageVO =
                    new UsageStorageVO(templateId, zoneId, event.getAccountId(), acct.getDomainId(), StorageTypes.TEMPLATE, event.getTemplateId(), templateSize,
                            event.getVirtualSize(), event.getCreateDate(), null);
            _usageStorageDao.persist(storageVO);
        } else if (EventTypes.EVENT_TEMPLATE_DELETE.equals(event.getType())) {
            List<UsageStorageVO> storageVOs;
            if (zoneId != -1L) {
                storageVOs = _usageStorageDao.listByIdAndZone(event.getAccountId(), templateId, StorageTypes.TEMPLATE, zoneId);
            } else {
                storageVOs = _usageStorageDao.listById(event.getAccountId(), templateId, StorageTypes.TEMPLATE);
            }
            if (storageVOs.size() > 1) {
                logger.warn("More that one usage entry for storage: " + templateId + " assigned to account: " + event.getAccountId() +
                        "; marking them all as deleted...");
            }
            for (UsageStorageVO storageVO : storageVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting template: " + storageVO.getId() + " from account: " + storageVO.getAccountId());
                }
                storageVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageStorageDao.update(storageVO);
            }
        }
    }

    private void createISOHelperEvent(UsageEventVO event) {
        long isoSize = -1L;

        long isoId = event.getResourceId();
        long zoneId = event.getZoneId();
        if (EventTypes.EVENT_ISO_CREATE.equals(event.getType()) || EventTypes.EVENT_ISO_COPY.equals(event.getType())) {
            isoSize = event.getSize();
        }

        if (EventTypes.EVENT_ISO_CREATE.equals(event.getType()) || EventTypes.EVENT_ISO_COPY.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("create iso with id : " + isoId + " for account: " + event.getAccountId());
            }
            List<UsageStorageVO> storageVOs = _usageStorageDao.listByIdAndZone(event.getAccountId(), isoId, StorageTypes.ISO, zoneId);
            if (storageVOs.size() > 0) {
                logger.warn("Usage entry for ISO: " + isoId + " assigned to account: " + event.getAccountId() + "already exists in zone " + zoneId);
                return;
            }
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageStorageVO storageVO =
                    new UsageStorageVO(isoId, zoneId, event.getAccountId(), acct.getDomainId(), StorageTypes.ISO, null, isoSize, isoSize, event.getCreateDate(), null);
            _usageStorageDao.persist(storageVO);
        } else if (EventTypes.EVENT_ISO_DELETE.equals(event.getType())) {
            List<UsageStorageVO> storageVOs;
            if (zoneId != -1L) {
                storageVOs = _usageStorageDao.listByIdAndZone(event.getAccountId(), isoId, StorageTypes.ISO, zoneId);
            } else {
                storageVOs = _usageStorageDao.listById(event.getAccountId(), isoId, StorageTypes.ISO);
            }

            if (storageVOs.size() > 1) {
                logger.warn("More that one usage entry for storage: " + isoId + " assigned to account: " + event.getAccountId() + "; marking them all as deleted...");
            }
            for (UsageStorageVO storageVO : storageVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting iso: " + storageVO.getId() + " from account: " + storageVO.getAccountId());
                }
                storageVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageStorageDao.update(storageVO);
            }
        }
    }

    private void createSnapshotHelperEvent(UsageEventVO event) {
        long snapSize = -1L;
        long zoneId = -1L;

        long snapId = event.getResourceId();
        if (EventTypes.EVENT_SNAPSHOT_CREATE.equals(event.getType())) {
            if (usageSnapshotSelection){
                snapSize =  event.getVirtualSize();
            }else {
                snapSize = event.getSize();
            }
            zoneId = event.getZoneId();
        }

        if (EventTypes.EVENT_SNAPSHOT_CREATE.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("create snapshot with id : " + snapId + " for account: " + event.getAccountId());
            }
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageStorageVO storageVO =
                    new UsageStorageVO(snapId, zoneId, event.getAccountId(), acct.getDomainId(), StorageTypes.SNAPSHOT, null, snapSize, event.getCreateDate(), null);
            _usageStorageDao.persist(storageVO);
        } else if (EventTypes.EVENT_SNAPSHOT_DELETE.equals(event.getType())) {
            List<UsageStorageVO> storageVOs = _usageStorageDao.listById(event.getAccountId(), snapId, StorageTypes.SNAPSHOT);
            if (storageVOs.size() > 1) {
                logger.warn("More that one usage entry for storage: " + snapId + " assigned to account: " + event.getAccountId() + "; marking them all as deleted...");
            }
            for (UsageStorageVO storageVO : storageVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting snapshot: " + storageVO.getId() + " from account: " + storageVO.getAccountId());
                }
                storageVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageStorageDao.update(storageVO);
            }
        }
    }

    private void createLoadBalancerHelperEvent(UsageEventVO event) {

        long zoneId = -1L;

        long id = event.getResourceId();

        if (EventTypes.EVENT_LOAD_BALANCER_CREATE.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating load balancer : " + id + " for account: " + event.getAccountId());
            }
            zoneId = event.getZoneId();
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageLoadBalancerPolicyVO lbVO = new UsageLoadBalancerPolicyVO(id, zoneId, event.getAccountId(), acct.getDomainId(), event.getCreateDate(), null);
            _usageLoadBalancerPolicyDao.persist(lbVO);
        } else if (EventTypes.EVENT_LOAD_BALANCER_DELETE.equals(event.getType())) {
            SearchCriteria<UsageLoadBalancerPolicyVO> sc = _usageLoadBalancerPolicyDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("lbId", SearchCriteria.Op.EQ, id);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsageLoadBalancerPolicyVO> lbVOs = _usageLoadBalancerPolicyDao.search(sc, null);
            if (lbVOs.size() > 1) {
                logger.warn("More that one usage entry for load balancer policy: " + id + " assigned to account: " + event.getAccountId() +
                        "; marking them all as deleted...");
            }
            for (UsageLoadBalancerPolicyVO lbVO : lbVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting load balancer policy: " + lbVO.getId() + " from account: " + lbVO.getAccountId());
                }
                lbVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageLoadBalancerPolicyDao.update(lbVO);
            }
        }
    }

    private void createPortForwardingHelperEvent(UsageEventVO event) {

        long zoneId = -1L;

        long id = event.getResourceId();

        if (EventTypes.EVENT_NET_RULE_ADD.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating port forwarding rule : " + id + " for account: " + event.getAccountId());
            }
            zoneId = event.getZoneId();
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsagePortForwardingRuleVO pfVO = new UsagePortForwardingRuleVO(id, zoneId, event.getAccountId(), acct.getDomainId(), event.getCreateDate(), null);
            _usagePortForwardingRuleDao.persist(pfVO);
        } else if (EventTypes.EVENT_NET_RULE_DELETE.equals(event.getType())) {
            SearchCriteria<UsagePortForwardingRuleVO> sc = _usagePortForwardingRuleDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("pfId", SearchCriteria.Op.EQ, id);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsagePortForwardingRuleVO> pfVOs = _usagePortForwardingRuleDao.search(sc, null);
            if (pfVOs.size() > 1) {
                logger.warn("More that one usage entry for port forwarding rule: " + id + " assigned to account: " + event.getAccountId() +
                        "; marking them all as deleted...");
            }
            for (UsagePortForwardingRuleVO pfVO : pfVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting port forwarding rule: " + pfVO.getId() + " from account: " + pfVO.getAccountId());
                }
                pfVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usagePortForwardingRuleDao.update(pfVO);
            }
        }
    }

    private void createNetworkOfferingEvent(UsageEventVO event) {

        long zoneId = -1L;

        long vmId = event.getResourceId();
        long networkOfferingId = event.getOfferingId();
        long nicId = 0;
        try {
            nicId = Long.parseLong(event.getResourceName());
        } catch (Exception e) {
            logger.warn("failed to get nic id from resource name, resource name is: " + event.getResourceName());
        }

        if (EventTypes.EVENT_NETWORK_OFFERING_CREATE.equals(event.getType()) || EventTypes.EVENT_NETWORK_OFFERING_ASSIGN.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating networking offering: " + networkOfferingId + " for Vm: " + vmId + " for account: " + event.getAccountId());
            }
            zoneId = event.getZoneId();
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            boolean isDefault = (event.getSize() == 1) ? true : false;
            UsageNetworkOfferingVO networkOffering =
                    new UsageNetworkOfferingVO(zoneId, event.getAccountId(), acct.getDomainId(), vmId, networkOfferingId, nicId, isDefault, event.getCreateDate(), null);
            _usageNetworkOfferingDao.persist(networkOffering);
        } else if (EventTypes.EVENT_NETWORK_OFFERING_DELETE.equals(event.getType()) || EventTypes.EVENT_NETWORK_OFFERING_REMOVE.equals(event.getType())) {
            SearchCriteria<UsageNetworkOfferingVO> sc = _usageNetworkOfferingDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, vmId);
            sc.addAnd("nicId", SearchCriteria.Op.EQ, nicId);
            sc.addAnd("networkOfferingId", SearchCriteria.Op.EQ, networkOfferingId);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsageNetworkOfferingVO> noVOs = _usageNetworkOfferingDao.search(sc, null);
            if (noVOs.size() > 1) {
                logger.warn("More that one usage entry for networking offering: " + networkOfferingId + " for Vm: " + vmId + " assigned to account: " +
                        event.getAccountId() + "; marking them all as deleted...");
            }
            for (UsageNetworkOfferingVO noVO : noVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting network offering: " + noVO.getNetworkOfferingId() + " from Vm: " + noVO.getVmInstanceId());
                }
                noVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageNetworkOfferingDao.update(noVO);
            }
        }
    }

    /**
     * Handles VPN user create and remove events:
     * <ul>
     *     <li>EventTypes#EVENT_VPN_USER_ADD</li>
     *     <li>EventTypes#EVENT_VPN_USER_ADD</li>
     * </ul>
     * if the event received by this method is neither add nor remove, we ignore it.
     */
    protected void handleVpnUserEvent(UsageEventVO event) {
        long accountId = event.getAccountId();
        Account account = _accountDao.findByIdIncludingRemoved(accountId);
        long zoneId = event.getZoneId();
        long userId = event.getResourceId();

        switch (event.getType()) {
            case EventTypes.EVENT_VPN_USER_ADD:
                createUsageVpnUser(event, account);
                break;
            case EventTypes.EVENT_VPN_USER_REMOVE:
                deleteUsageVpnUser(event, account);
                break;
            default:
                logger.debug("The event [type={}, zoneId={}, accountId={}, userId={}, resourceName={}, createDate={}] is neither of type [{}] nor [{}].",
                        event.getType(), zoneId, accountId, userId, event.getResourceName(), DateUtil.displayDateInTimezone(usageAggregationTimeZone, event.getCreateDate()),
                        EventTypes.EVENT_VPN_USER_ADD, EventTypes.EVENT_VPN_USER_REMOVE);
        }
    }

    /**
     * Find and delete, if exists, usage VPN user entries
     */
    protected void deleteUsageVpnUser(UsageEventVO event, Account account) {
        long accountId = account.getId();
        long userId = event.getResourceId();
        long zoneId = event.getZoneId();
        long domainId = account.getDomainId();

        List<UsageVPNUserVO> usageVpnUsers = findUsageVpnUsers(accountId, zoneId, userId, domainId);

        if (CollectionUtils.isEmpty(usageVpnUsers)) {
            logger.warn(String.format("No usage entry for vpn user [%s] assigned to account [%s] domain [%s] and zone [%s] was found.",
                    userId, accountId, domainId, zoneId));
        }
        if (usageVpnUsers.size() > 1) {
            logger.warn(String.format("More than one usage entry for vpn user [%s] assigned to account [%s] domain [%s] and zone [%s]; marking them all as deleted.", userId,
                    accountId, domainId, zoneId));
        }
        for (UsageVPNUserVO vpnUser : usageVpnUsers) {
            logger.debug(String.format("Deleting vpn user [%s] assigned to account [%s] domain [%s] and zone [%s] that was created at [%s].", vpnUser.getUserId(),
                    vpnUser.getAccountId(), vpnUser.getDomainId(), vpnUser.getZoneId(), vpnUser.getCreated()));
            vpnUser.setDeleted(new Date());
            _usageVPNUserDao.update(vpnUser);
        }
    }

    /**
     * Creates an entry for the Usage VPN User.
     * If there is already an entry in the database with the same accountId, domainId, userId and zoneId, we do not persist a new entry.
     */
    protected void createUsageVpnUser(UsageEventVO event, Account account) {
        long accountId = account.getId();
        long userId = event.getResourceId();
        long zoneId = event.getZoneId();
        long domainId = account.getDomainId();

        List<UsageVPNUserVO> usageVpnUsers = findUsageVpnUsers(accountId, zoneId, userId, domainId);

        if (usageVpnUsers.size() > 0) {
            logger.debug(String.format("We do not need to create the usage VPN user [%s] assigned to account [%s] because it already exists.", userId, accountId));
        } else {
            logger.debug(String.format("Creating VPN user [%s] assigned to account [%s] domain [%s], zone [%s], and created at [%s]", userId, accountId, domainId, zoneId,
                    event.getCreateDate()));
            UsageVPNUserVO vpnUser = new UsageVPNUserVO(zoneId, accountId, domainId, userId, event.getResourceName(), event.getCreateDate(), null);
            _usageVPNUserDao.persist(vpnUser);
        }
    }

    protected List<UsageVPNUserVO> findUsageVpnUsers(long accountId, long zoneId, long userId, long domainId) {
        SearchCriteria<UsageVPNUserVO> sc = _usageVPNUserDao.createSearchCriteria();
        sc.addAnd("zoneId", SearchCriteria.Op.EQ, zoneId);
        sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
        sc.addAnd("userId", SearchCriteria.Op.EQ, userId);
        sc.addAnd("deleted", SearchCriteria.Op.NULL);
        return _usageVPNUserDao.search(sc, null);
    }

    private void createSecurityGroupEvent(UsageEventVO event) {

        long zoneId = -1L;

        long vmId = event.getResourceId();
        long sgId = event.getOfferingId();

        if (EventTypes.EVENT_SECURITY_GROUP_ASSIGN.equals(event.getType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("Assigning : security group" + sgId + " to Vm: " + vmId + " for account: " + event.getAccountId());
            }
            zoneId = event.getZoneId();
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            UsageSecurityGroupVO securityGroup = new UsageSecurityGroupVO(zoneId, event.getAccountId(), acct.getDomainId(), vmId, sgId, event.getCreateDate(), null);
            _usageSecurityGroupDao.persist(securityGroup);
        } else if (EventTypes.EVENT_SECURITY_GROUP_REMOVE.equals(event.getType())) {
            SearchCriteria<UsageSecurityGroupVO> sc = _usageSecurityGroupDao.createSearchCriteria();
            sc.addAnd("accountId", SearchCriteria.Op.EQ, event.getAccountId());
            sc.addAnd("vmInstanceId", SearchCriteria.Op.EQ, vmId);
            sc.addAnd("securityGroupId", SearchCriteria.Op.EQ, sgId);
            sc.addAnd("deleted", SearchCriteria.Op.NULL);
            List<UsageSecurityGroupVO> sgVOs = _usageSecurityGroupDao.search(sc, null);
            if (sgVOs.size() > 1) {
                logger.warn("More that one usage entry for security group: " + sgId + " for Vm: " + vmId + " assigned to account: " + event.getAccountId() +
                        "; marking them all as deleted...");
            }
            for (UsageSecurityGroupVO sgVO : sgVOs) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting security group: " + sgVO.getSecurityGroupId() + " from Vm: " + sgVO.getVmInstanceId());
                }
                sgVO.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageSecurityGroupDao.update(sgVO);
            }
        }
    }

    /**
     * Handles Vm Snapshot create and delete events:
     * <ul>
     *     <li>EventTypes#EVENT_VM_SNAPSHOT_CREATE</li>
     *     <li>EventTypes#EVENT_VM_SNAPSHOT_DELETE</li>
     * </ul>
     * if the event received by this method is neither add nor remove, we ignore it.
     */
    protected void handleVMSnapshotEvent(UsageEventVO event) {
        switch (event.getType()) {
            case EventTypes.EVENT_VM_SNAPSHOT_CREATE:
                createUsageVMSnapshot(event);
                break;
            case EventTypes.EVENT_VM_SNAPSHOT_DELETE:
                deleteUsageVMSnapshot(event);
                break;
            default:
                logger.debug("The event [type={}, zoneId={}, accountId={}, resourceName={}, diskOfferingId={}, createDate={}] is neither of type [{}] nor [{}].",
                        event.getType(), event.getZoneId(), event.getAccountId(), event.getResourceName(), event.getOfferingId(),
                        DateUtil.displayDateInTimezone(usageAggregationTimeZone, event.getCreateDate()), EventTypes.EVENT_VM_SNAPSHOT_CREATE,
                        EventTypes.EVENT_VM_SNAPSHOT_DELETE);
        }
    }

    /**
     * Creates an entry for the Usage VM Snapshot.
     */
    protected void createUsageVMSnapshot(UsageEventVO event) {
        long accountId = event.getAccountId();
        Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
        long domainId = acct.getDomainId();
        Long offeringId = event.getOfferingId();
        long vmId = event.getResourceId();
        long volumeId = event.getTemplateId();
        long zoneId = event.getZoneId();
        Date created = event.getCreateDate();
        long size = (event.getSize() == null) ? 0 : event.getSize();

        UsageEventDetailsVO detailVO = _usageEventDetailsDao.findDetail(event.getId(), UsageEventVO.DynamicParameters.vmSnapshotId.name());
        Long vmSnapshotId = null;
        if (detailVO != null) {
            String snapId = detailVO.getValue();
            vmSnapshotId = Long.valueOf(snapId);
        }
        logger.debug("Creating usage VM Snapshot for VM id [{}] assigned to account [{}] domain [{}], zone [{}], and created at [{}].",
                vmId, accountId, domainId, zoneId, DateUtil.displayDateInTimezone(usageAggregationTimeZone, event.getCreateDate()));
        UsageVMSnapshotVO vsVO = new UsageVMSnapshotVO(volumeId, zoneId, accountId, domainId, vmId, offeringId, size, created, null);
        vsVO.setVmSnapshotId(vmSnapshotId);
        _usageVMSnapshotDao.persist(vsVO);
    }

    /**
     * Find and delete, if exists, usage VM Snapshots entries
     */
    protected void deleteUsageVMSnapshot(UsageEventVO event) {
        long accountId = event.getAccountId();
        Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
        Long domainId = acct.getDomainId();
        Long diskOfferingId = event.getOfferingId();
        long vmId = event.getResourceId();
        long zoneId = event.getZoneId();
        List<UsageVMSnapshotVO> usageVMSnapshots = findUsageVMSnapshots(accountId, zoneId, domainId, vmId, diskOfferingId);
        if (CollectionUtils.isEmpty(usageVMSnapshots)){
            logger.warn(String.format("No usage entry for VM snapshot for VM id [%s] assigned to account [%s] domain [%s] and zone [%s] was found.",
                    vmId, accountId, domainId, zoneId));
        }
        if (usageVMSnapshots.size() > 1) {
            logger.warn(String.format("More than one usage entry for VM snapshot for VM id [%s] assigned to account [%s] domain [%s] and zone [%s]; marking them all as deleted.", vmId,
                    accountId, domainId, zoneId));
        }
        for (UsageVMSnapshotVO vmSnapshots : usageVMSnapshots) {
            logger.debug(String.format("Deleting VM Snapshot for VM id [%s] assigned to account [%s] domain [%s] and zone [%s] that was created at [%s].", vmSnapshots.getVmId(),
                    vmSnapshots.getAccountId(), vmSnapshots.getDomainId(), vmSnapshots.getZoneId(), vmSnapshots.getCreated()));
            vmSnapshots.setProcessed(event.getCreateDate());
            _usageVMSnapshotDao.update(vmSnapshots);
        }
    }

    protected List<UsageVMSnapshotVO> findUsageVMSnapshots(long accountId, long zoneId, long domainId, long vmId, Long diskOfferingId) {
        SearchCriteria<UsageVMSnapshotVO> sc = _usageVMSnapshotDao.createSearchCriteria();
        sc.addAnd("zoneId", SearchCriteria.Op.EQ, zoneId);
        sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
        sc.addAnd("vmId", SearchCriteria.Op.EQ, vmId);
        sc.addAnd("diskOfferingId", SearchCriteria.Op.EQ, diskOfferingId);
        sc.addAnd("processed", SearchCriteria.Op.NULL);
        return _usageVMSnapshotDao.search(sc, null);
    }

    private void createVmSnapshotOnPrimaryEvent(UsageEventVO event) {
        Long vmId = event.getResourceId();
        String name = event.getResourceName();
        if (EventTypes.EVENT_VM_SNAPSHOT_ON_PRIMARY.equals(event.getType())) {
            Long zoneId = event.getZoneId();
            Long accountId = event.getAccountId();
            long physicalsize = (event.getSize() == null) ? 0 : event.getSize();
            long virtualsize = (event.getVirtualSize() == null) ? 0 : event.getVirtualSize();
            Date created = event.getCreateDate();
            Account acct = _accountDao.findByIdIncludingRemoved(event.getAccountId());
            Long domainId = acct.getDomainId();

            UsageEventDetailsVO detailVO = _usageEventDetailsDao.findDetail(event.getId(), UsageEventVO.DynamicParameters.vmSnapshotId.name());
            Long vmSnapshotId = null;
            if (detailVO != null) {
                String snapId = detailVO.getValue();
                vmSnapshotId = Long.valueOf(snapId);
            }
            UsageSnapshotOnPrimaryVO vsVO = new UsageSnapshotOnPrimaryVO(vmId, zoneId, accountId, domainId, vmId, name, 0, virtualsize, physicalsize, created, null);
            vsVO.setVmSnapshotId(vmSnapshotId);
            if (logger.isDebugEnabled()) {
                logger.debug("createSnapshotOnPrimaryEvent UsageSnapshotOnPrimaryVO " + vsVO);
            }
            _usageSnapshotOnPrimaryDao.persist(vsVO);
        } else if (EventTypes.EVENT_VM_SNAPSHOT_OFF_PRIMARY.equals(event.getType())) {
            QueryBuilder<UsageSnapshotOnPrimaryVO> sc = QueryBuilder.create(UsageSnapshotOnPrimaryVO.class);
            sc.and(sc.entity().getAccountId(), SearchCriteria.Op.EQ, event.getAccountId());
            sc.and(sc.entity().getVmId(), SearchCriteria.Op.EQ, vmId);
            sc.and(sc.entity().getName(), SearchCriteria.Op.EQ, name);
            sc.and(sc.entity().getDeleted(), SearchCriteria.Op.NULL);
            List<UsageSnapshotOnPrimaryVO> vmsnaps = sc.list();
            if (vmsnaps.size() > 1) {
                logger.warn("More that one usage entry for vm snapshot: " + name + " for vm id:" + vmId + " assigned to account: " + event.getAccountId()
                        + "; marking them all as deleted...");
            }
            for (UsageSnapshotOnPrimaryVO vmsnap : vmsnaps) {
                if (logger.isDebugEnabled()) {
                    logger.debug("deleting vm snapshot name: " + vmsnap.getName() + " from account: " + vmsnap.getAccountId());
                }
                vmsnap.setDeleted(event.getCreateDate()); // there really shouldn't be more than one
                _usageSnapshotOnPrimaryDao.updateDeleted(vmsnap);
            }
        }
    }

    private void createBackupEvent(final UsageEventVO event) {
        Long vmId = event.getResourceId();
        Long zoneId = event.getZoneId();
        Long accountId = event.getAccountId();
        Long backupOfferingId = event.getOfferingId();
        Account account = _accountDao.findByIdIncludingRemoved(event.getAccountId());
        Long domainId = account.getDomainId();
        Date created = event.getCreateDate();

        if (EventTypes.EVENT_VM_BACKUP_OFFERING_ASSIGN.equals(event.getType())) {
            final UsageBackupVO backupVO = new UsageBackupVO(zoneId, accountId, domainId, vmId, backupOfferingId, created);
            usageBackupDao.persist(backupVO);
        } else if (EventTypes.EVENT_VM_BACKUP_OFFERING_REMOVED_AND_BACKUPS_DELETED.equals(event.getType())) {
            usageBackupDao.removeUsage(accountId, vmId, backupOfferingId, event.getCreateDate());
        } else if (EventTypes.EVENT_VM_BACKUP_USAGE_METRIC.equals(event.getType())) {
            usageBackupDao.updateMetrics(vmId, backupOfferingId, event.getSize(), event.getVirtualSize());
        }
    }

    private void handleNetworkEvent(UsageEventVO event) {
        String eventType = event.getType();
        if (EventTypes.EVENT_NETWORK_DELETE.equals(eventType)) {
            removeNetworkHelperEntry(event);
        } else if (EventTypes.EVENT_NETWORK_CREATE.equals(eventType)) {
            createNetworkHelperEntry(event);
        } else if (EventTypes.EVENT_NETWORK_UPDATE.equals(eventType)) {
            updateNetworkHelperEntry(event);
        } else {
            logger.error(String.format("Unknown event type [%s] in Networks event parser. Skipping it.", eventType));
        }
    }

    private void removeNetworkHelperEntry(UsageEventVO event) {
        long networkId = event.getResourceId();
        logger.debug(String.format("Removing helper entries of network [%s].", networkId));
        usageNetworksDao.remove(networkId, event.getCreateDate());
    }

    private void createNetworkHelperEntry(UsageEventVO event) {
        long networkId = event.getResourceId();
        Account account = _accountDao.findByIdIncludingRemoved(event.getAccountId());
        long domainId = account.getDomainId();

        List<UsageNetworksVO> entries = usageNetworksDao.listAll(networkId);
        if (!entries.isEmpty()) {
            logger.warn(String.format("Received a NETWORK.CREATE event for a network [%s] that already has helper entries; " +
                    "therefore, we will not create a new one.", networkId));
            return;
        }

        logger.debug(String.format("Creating a helper entry for network [%s].", networkId));
        UsageNetworksVO usageNetworksVO = new UsageNetworksVO(networkId, event.getOfferingId(), event.getZoneId(),
                event.getAccountId(), domainId, Network.State.Allocated.name(), event.getCreateDate(), null);
        usageNetworksDao.persist(usageNetworksVO);
    }

    private void updateNetworkHelperEntry(UsageEventVO event) {
        long networkId = event.getResourceId();
        Account account = _accountDao.findByIdIncludingRemoved(event.getAccountId());
        long domainId = account.getDomainId();

        logger.debug(String.format("Marking previous helper entries of network [%s] as removed.", networkId));
        usageNetworksDao.remove(networkId, event.getCreateDate());

        logger.debug(String.format("Creating an updated helper entry for network [%s].", networkId));
        UsageNetworksVO usageNetworksVO = new UsageNetworksVO(networkId, event.getOfferingId(), event.getZoneId(),
                event.getAccountId(), domainId, event.getResourceType(), event.getCreateDate(), null);
        usageNetworksDao.persist(usageNetworksVO);
    }

    private void handleVpcEvent(UsageEventVO event) {
        String eventType = event.getType();
        if (EventTypes.EVENT_VPC_DELETE.equals(eventType)) {
            removeVpcHelperEntry(event);
        } else if (EventTypes.EVENT_VPC_CREATE.equals(eventType)) {
            createVpcHelperEntry(event);
        } else {
            logger.error(String.format("Unknown event type [%s] in VPC event parser. Skipping it.", eventType));
        }
    }

    private void removeVpcHelperEntry(UsageEventVO event) {
        long vpcId = event.getResourceId();
        logger.debug(String.format("Removing helper entries of VPC [%s].", vpcId));
        usageVpcDao.remove(vpcId, event.getCreateDate());
    }

    private void createVpcHelperEntry(UsageEventVO event) {
        long vpcId = event.getResourceId();
        Account account = _accountDao.findByIdIncludingRemoved(event.getAccountId());
        long domainId = account.getDomainId();

        List<UsageVpcVO> entries = usageVpcDao.listAll(vpcId);
        if (!entries.isEmpty()) {
            logger.warn(String.format("Active helper entries already exist for VPC [%s]; therefore, we will not create a new one.",
                    vpcId));
            return;
        }

        logger.debug(String.format("Creating a helper entry for VPC [%s].", vpcId));
        UsageVpcVO usageVPCVO = new UsageVpcVO(vpcId, event.getZoneId(), event.getAccountId(), domainId, Vpc.State.Enabled.name(), event.getCreateDate(), null);
        usageVpcDao.persist(usageVPCVO);
    }

    private class Heartbeat extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            TransactionLegacy usageTxn = TransactionLegacy.open(TransactionLegacy.USAGE_DB);
            try {
                if (!_heartbeatLock.lock(3)) { // 3 second timeout
                    if (logger.isTraceEnabled())
                        logger.trace("Heartbeat lock is in use by others, returning true as someone else will take over the job if required");
                    return;
                }

                try {
                    // check for one-off jobs
                    UsageJobVO nextJob = _usageJobDao.getNextImmediateJob();
                    if (nextJob != null) {
                        if (_hostname.equals(nextJob.getHost()) && (_pid == nextJob.getPid().intValue())) {
                            updateJob(nextJob.getId(), null, null, null, UsageJobVO.JOB_SCHEDULED);
                            scheduleParse();
                        }
                    }

                    Long jobId = _usageJobDao.checkHeartbeat(_hostname, _pid, _aggregationDuration);
                    if (jobId != null) {
                        // if I'm taking over the job...see how long it's been since the last job, and if it's more than the
                        // aggregation range...do a one off job to catch up.  However, only do this if we are more than half
                        // the aggregation range away from executing the next job
                        long now = System.currentTimeMillis();
                        long timeToJob = _jobExecTime.getTimeInMillis() - now;
                        long timeSinceLastSuccessJob = 0;
                        long aggregationDurationMillis = _aggregationDuration * 60L * 1000L;
                        long lastSuccess = _usageJobDao.getLastJobSuccessDateMillis();
                        if (lastSuccess > 0) {
                            timeSinceLastSuccessJob = now - lastSuccess;
                        }

                        if ((timeSinceLastSuccessJob > 0) && (timeSinceLastSuccessJob > (aggregationDurationMillis - 100))) {
                            if (timeToJob > (aggregationDurationMillis / 2)) {
                                logger.debug("it's been {} ms since last usage job and {} ms until next job, scheduling an immediate job to catch up (aggregation duration is {} minutes)"
                                    , timeSinceLastSuccessJob, timeToJob, _aggregationDuration);
                                scheduleParse();
                            }
                        }

                        boolean changeOwner = updateJob(jobId, _hostname, Integer.valueOf(_pid), new Date(), UsageJobVO.JOB_NOT_SCHEDULED);
                        if (changeOwner) {
                            deleteOneOffJobs(_hostname, _pid);
                        }
                    }
                } finally {
                    _heartbeatLock.unlock();
                }
            } catch (Exception ex) {
                logger.error("error in heartbeat", ex);
            } finally {
                usageTxn.close();
            }
        }

        @DB
        protected boolean updateJob(Long jobId, String hostname, Integer pid, Date heartbeat, int scheduled) {
            boolean changeOwner = false;
            TransactionLegacy txn = TransactionLegacy.currentTxn();
            try {
                txn.start();

                // take over the job, setting our hostname/pid/heartbeat time
                UsageJobVO job = _usageJobDao.lockRow(jobId, Boolean.TRUE);
                if (!job.getHost().equals(hostname) || !job.getPid().equals(pid)) {
                    changeOwner = true;
                }

                UsageJobVO jobForUpdate = _usageJobDao.createForUpdate();
                if (hostname != null) {
                    jobForUpdate.setHost(hostname);
                }
                if (pid != null) {
                    jobForUpdate.setPid(pid);
                }
                if (heartbeat != null) {
                    jobForUpdate.setHeartbeat(heartbeat);
                }
                jobForUpdate.setScheduled(scheduled);
                _usageJobDao.update(job.getId(), jobForUpdate);

                txn.commit();
            } catch (Exception dbEx) {
                txn.rollback();
                logger.error("error updating usage job", dbEx);
            }
            return changeOwner;
        }

        @DB
        protected void deleteOneOffJobs(String hostname, int pid) {
            SearchCriteria<UsageJobVO> sc = _usageJobDao.createSearchCriteria();
            SearchCriteria<UsageJobVO> ssc = _usageJobDao.createSearchCriteria();
            ssc.addOr("host", SearchCriteria.Op.NEQ, hostname);
            ssc.addOr("pid", SearchCriteria.Op.NEQ, pid);
            sc.addAnd("host", SearchCriteria.Op.SC, ssc);
            sc.addAnd("endMillis", SearchCriteria.Op.EQ, Long.valueOf(0));
            sc.addAnd("jobType", SearchCriteria.Op.EQ, Integer.valueOf(UsageJobVO.JOB_TYPE_SINGLE));
            sc.addAnd("scheduled", SearchCriteria.Op.EQ, Integer.valueOf(0));
            _usageJobDao.expunge(sc);
        }
    }

    private class SanityCheck extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            logger.info("running sanity check");
            UsageSanityChecker usc = new UsageSanityChecker();
            try {
                String errors = usc.runSanityCheck();
                if (errors.length() > 0) {
                    _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_USAGE_SANITY_RESULT, 0, new Long(0), "Usage Sanity Check failed", errors);
                } else {
                    _alertMgr.clearAlert(AlertManager.AlertType.ALERT_TYPE_USAGE_SANITY_RESULT, 0, 0);
                }
            } catch (SQLException e) {
                logger.error("Error in sanity check", e);
            }
        }
    }

    private class AbandonJob extends Thread {
        @Override
        public void run() {
            logger.info("exiting Usage Manager");
            _usageJobDao.removeLastOpenJobsOwned(_hostname, _pid);
        }
    }
}

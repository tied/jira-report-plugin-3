package com.atlassian.plugins.tutorial.jira.reports;

import com.atlassian.core.util.DateUtils;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.plugin.report.impl.AbstractReport;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Scanned
public class CreationReport extends AbstractReport {
    private static final Logger log = Logger.getLogger(CreationReport.class);
    private static final int MAX_HEIGHT = 360;
    private long maxCount = 0;
    private Collection<Long> openIssuesCounts = new ArrayList<>();
    private Collection<String> formattedDates = new ArrayList<>();
    @JiraImport
    private final SearchProvider searchProvider;
    @JiraImport
    private final ProjectManager projectManager;
    private final DateTimeFormatter formatter;

    private Date startDate;
    private Date endDate;
    private Long interval;
    private Long projectId;

    public CreationReport(SearchProvider searchProvider, ProjectManager projectManager,
                          @JiraImport DateTimeFormatterFactory dateTimeFormatterFactory) {
        this.searchProvider = searchProvider;
        this.projectManager = projectManager;
        this.formatter = dateTimeFormatterFactory.formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser();
    }


    public String generateReportHtml(ProjectActionSupport action, Map params) throws Exception {
        //action.getLoggedInUser() since Jira 7.0.
        //getLoggedInApplicationUser() since Jira 5.2
        fillIssuesCounts(startDate, endDate, interval, action.getLoggedInUser(), projectId);
        List<Number> issueBarHeights = new ArrayList<>();
        if (maxCount > 0) {
            openIssuesCounts.forEach(issueCount ->
                    issueBarHeights.add((issueCount.floatValue() / maxCount) * MAX_HEIGHT)
            );
        }
        Map<String, Object> velocityParams = new HashMap<>();
        velocityParams.put("startDate", formatter.format(startDate));
        velocityParams.put("endDate", formatter.format(endDate));
        velocityParams.put("openCount", openIssuesCounts);
        velocityParams.put("issueBarHeights", issueBarHeights);
        velocityParams.put("dates", formattedDates);
        velocityParams.put("maxHeight", MAX_HEIGHT);
        velocityParams.put("projectName", projectManager.getProjectObj(projectId).getName());
        velocityParams.put("interval", interval);
        return descriptor.getHtml("view", velocityParams);
    }

    private long getOpenIssueCount(ApplicationUser user, Date startDate, Date endDate, Long projectId) throws SearchException {
        JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder();
        Query query = queryBuilder.where().createdBetween(startDate, endDate).and().project(projectId).buildQuery();
        return searchProvider.searchCount(query, user);
    }

    private void fillIssuesCounts(Date startDate, Date endDate, Long interval, ApplicationUser user, Long projectId) throws SearchException {
        long intervalValue = interval * DateUtils.DAY_MILLIS;
        Date newStartDate;
        long count;
        while (startDate.before(endDate)) {
            newStartDate = new Date(startDate.getTime() + intervalValue);
            if (newStartDate.after(endDate))
                count = getOpenIssueCount(user, startDate, endDate, projectId);
            else
                count = getOpenIssueCount(user, startDate, newStartDate, projectId);
            if (maxCount < count)
                maxCount = count;
            openIssuesCounts.add(count);
            formattedDates.add(formatter.format(startDate));
            startDate = newStartDate;
        }
    }

    public void validate(ProjectActionSupport action, Map params) {
        try {
            startDate = formatter.parse(ParameterUtils.getStringParam(params, "startDate"));
        } catch (IllegalArgumentException e) {
            action.addError("startDate", action.getText("report.issuecreation.startdate.required"));
            log.error("Exception while parsing startDate");
        }
        try {
            endDate = formatter.parse(ParameterUtils.getStringParam(params, "endDate"));
        } catch (IllegalArgumentException e) {
            action.addError("endDate", action.getText("report.issuecreation.enddate.required"));
            log.error("Exception while parsing endDate");
        }

        interval = ParameterUtils.getLongParam(params, "interval");
        projectId = ParameterUtils.getLongParam(params, "selectedProjectId");
        if (interval == null || interval <= 0) {
            action.addError("interval", action.getText("report.issuecreation.interval.invalid"));
            log.error("Invalid interval");
        }
        if (projectId == null || projectManager.getProjectObj(projectId) == null){
            action.addError("selectedProjectId", action.getText("report.issuecreation.projectid.invalid"));
            log.error("Invalid projectId");
        }
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            action.addError("endDate", action.getText("report.issuecreation.before.startdate"));
            log.error("Invalid dates: start date should be before end date");
        }
    }
}
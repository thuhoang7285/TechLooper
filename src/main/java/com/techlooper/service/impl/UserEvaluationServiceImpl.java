package com.techlooper.service.impl;

import com.techlooper.entity.JobEntity;
import com.techlooper.entity.SalaryReview;
import com.techlooper.model.SalaryRange;
import com.techlooper.model.SalaryReport;
import com.techlooper.model.SalaryReviewSurvey;
import com.techlooper.model.TopPaidJob;
import com.techlooper.repository.elasticsearch.SalaryReviewRepository;
import com.techlooper.service.JobQueryBuilder;
import com.techlooper.service.JobSearchService;
import com.techlooper.service.SalaryReviewService;
import com.techlooper.service.UserEvaluationService;
import com.techlooper.util.ExcelUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.elasticsearch.action.search.SearchType;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by NguyenDangKhoa on 3/19/15.
 */
@Service
public class UserEvaluationServiceImpl implements UserEvaluationService {

    private static final int MINIMUM_NUMBER_OF_JOBS = 10;

    private static final int TWO_PERCENTILES = 2;

    @Resource
    private JobSearchService jobSearchService;

    @Resource
    private ElasticsearchTemplate elasticsearchTemplate;

    @Resource
    private SalaryReviewRepository salaryReviewRepository;

    @Resource
    private JobQueryBuilder jobQueryBuilder;

    @Resource
    private Environment environment;

    @Resource
    private SalaryReviewService salaryReviewService;

    private static final double[] percents = new double[]{10D, 25D, 50D, 75D, 90D};

    public void evaluateJobOffer(SalaryReview salaryReview) {
        NativeSearchQueryBuilder queryBuilder = jobQueryBuilder.getJobSearchQueryForSalaryReview(salaryReview);

        // Get the list of jobs search result for user percentile rank calculation
        List<JobEntity> jobs = getJobSearchResult(queryBuilder);

        // In order to make our report more accurate, we should add data from generated report in calculation
        List<SalaryReview> salaryReviews = salaryReviewService.searchSalaryReview(salaryReview);
        mergeSalaryReviewWithJobList(jobs, salaryReviews);

        // In case total number of jobs search result is less than 10, add more jobs from search by skills
        if (jobs.size() > 0 && jobs.size() < MINIMUM_NUMBER_OF_JOBS && salaryReview.getSkills() != null && !salaryReview.getSkills().isEmpty()) {
            NativeSearchQueryBuilder higherSalaryQueryBuilder = jobQueryBuilder.getJobSearchQueryBySkill(salaryReview);
            List<JobEntity> jobBySkills = getJobSearchResult(higherSalaryQueryBuilder);
            jobs.addAll(jobBySkills);
        }

        // It's only enabled on test scope for checking whether our calculation is right or wrong
        boolean isExportToExcel = environment.getProperty("salaryEvaluation.exportResultToExcel", Boolean.class) != null ?
                environment.getProperty("salaryEvaluation.exportResultToExcel", Boolean.class) : false;
        if (isExportToExcel) {
            ExcelUtils.exportSalaryReport(jobs);
        }

        calculateSalaryPercentile(salaryReview, jobs);

        // Save user salary information should happen only in production environment
        boolean allowToSave = environment.getProperty("salaryEvaluation.allowToSave", Boolean.class) != null ?
                environment.getProperty("salaryEvaluation.allowToSave", Boolean.class) : false;
        if (allowToSave) {
            salaryReviewRepository.save(salaryReview);
        }

        // get top 3 higher salary jobs
        List<TopPaidJob> topPaidJobs = findTopPaidJob(jobSearchService.getHigherSalaryJobs(salaryReview), salaryReview.getNetSalary());
        salaryReview.setTopPaidJobs(topPaidJobs);
    }

    private void calculateSalaryPercentile(SalaryReview salaryReview, List<JobEntity> jobs) {
        SalaryReport salaryReport = new SalaryReport();
        salaryReport.setNetSalary(salaryReview.getNetSalary());

        double[] salaries = extractSalariesFromJob(jobs);
        List<SalaryRange> salaryRanges = new ArrayList<>();
        Percentile percentile = new Percentile();
        for (double percent : percents) {
            salaryRanges.add(new SalaryRange(percent, Math.floor(percentile.evaluate(salaries, percent))));
        }
        salaryReport.setSalaryRanges(salaryRanges);

        // Calculate salary percentile rank for user based on list of salary percentiles from above result
        double percentRank = calculatePercentPosition(salaryReport);
        salaryReport.setPercentRank(Math.floor(percentRank));

        salaryReview.setSalaryReport(salaryReport);
        salaryReview.setCreatedDateTime(new Date().getTime());
    }

    private double[] extractSalariesFromJob(List<JobEntity> jobs) {
        return jobs.stream().mapToDouble(job ->
                jobSearchService.getAverageSalary(job.getSalaryMin(), job.getSalaryMax())).toArray();
    }

    private double calculatePercentPosition(SalaryReport salaryReport) {
        //Remove duplicated percentiles if any
        List<SalaryRange> noDuplicatedSalaryRanges = salaryReport.getSalaryRanges().stream().distinct().collect(Collectors.toList());
        if (noDuplicatedSalaryRanges.size() >= TWO_PERCENTILES) {
            int size = noDuplicatedSalaryRanges.size();
            int i = 0;
            while (i < size && salaryReport.getNetSalary() >= noDuplicatedSalaryRanges.get(i).getPercentile()) {
                i++;
            }

            double position = 0D;
            if (i == 0) {
                SalaryRange firstPercentile = noDuplicatedSalaryRanges.get(0);
                position = salaryReport.getNetSalary() / firstPercentile.getPercentile() * firstPercentile.getPercent();
            } else if (i == size) {
                SalaryRange lastPercentile = noDuplicatedSalaryRanges.get(size - 1);
                position = salaryReport.getNetSalary() / lastPercentile.getPercentile() * lastPercentile.getPercent();
            } else {
                SalaryRange lessPercentile = noDuplicatedSalaryRanges.get(i - 1);
                SalaryRange greaterPercentile = noDuplicatedSalaryRanges.get(i);
                double relativePercentBetweenTwoPercentile = (salaryReport.getNetSalary() - lessPercentile.getPercentile()) /
                        (greaterPercentile.getPercentile() - lessPercentile.getPercentile());
                position = (greaterPercentile.getPercent() - lessPercentile.getPercent()) * relativePercentBetweenTwoPercentile
                        + lessPercentile.getPercent();
            }

            salaryReport.setSalaryRanges(noDuplicatedSalaryRanges);

            position = Math.floor(position);
            if (position == 0D) {
                return 1D;
            } else if (position >= 100D) {
                return 99D;
            } else {
                return position;
            }
        }
        return Double.NaN;
    }

    private List<TopPaidJob> findTopPaidJob(List<JobEntity> higherSalaryJobs, Integer netSalary) {
        List<TopPaidJob> topPaidJobs = new ArrayList<>();
        for (JobEntity jobEntity : higherSalaryJobs) {
            double addedPercent = (jobSearchService.getAverageSalary(jobEntity.getSalaryMin(), jobEntity.getSalaryMax()) - netSalary) /
                    netSalary * 100;
            topPaidJobs.add(new TopPaidJob(jobEntity.getId(), jobEntity.getJobTitle(), jobEntity.getCompanyDesc(), Math.ceil(addedPercent)));
        }
        return topPaidJobs;
    }

    private void mergeSalaryReviewWithJobList(List<JobEntity> jobs, List<SalaryReview> salaryReviews) {
        for (SalaryReview salaryReview : salaryReviews) {
            JobEntity jobEntity = new JobEntity();
            jobEntity.setId(salaryReview.getCreatedDateTime().toString());
            jobEntity.setJobTitle(salaryReview.getJobTitle());
            jobEntity.setSalaryMin(salaryReview.getNetSalary().longValue());
            jobEntity.setSalaryMax(salaryReview.getNetSalary().longValue());
            jobs.add(jobEntity);
        }
    }

    private List<JobEntity> getJobSearchResult(NativeSearchQueryBuilder queryBuilder) {
        queryBuilder.withSearchType(SearchType.DEFAULT);
        long totalJobs = elasticsearchTemplate.count(queryBuilder.build());
        long totalPage = totalJobs % 100 == 0 ? totalJobs / 100 : totalJobs / 100 + 1;
        int pageIndex = 0;
        List<JobEntity> jobs = new ArrayList<>();
        while (pageIndex < totalPage) {
            queryBuilder.withPageable(new PageRequest(pageIndex, 100));
            jobs.addAll(elasticsearchTemplate.queryForPage(queryBuilder.build(), JobEntity.class).getContent());
            pageIndex++;
        }
        return jobs;
    }

    public void deleteSalaryReview(SalaryReview salaryReview) {
        salaryReviewRepository.delete(salaryReview.getCreatedDateTime());
    }

    public boolean saveSalaryReviewSurvey(SalaryReviewSurvey salaryReviewSurvey) {
        SalaryReview salaryReview = salaryReviewRepository.findOne(salaryReviewSurvey.getSalaryReviewId());
        if (salaryReview != null) {
            salaryReview.setSalaryReviewSurvey(salaryReviewSurvey);
            salaryReviewRepository.save(salaryReview);
            return true;
        }
        return false;
    }

}

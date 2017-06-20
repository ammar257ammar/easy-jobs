package org.jeasy.jobs;

import org.jeasy.jobs.job.Job;
import org.jeasy.jobs.job.JobRepository;
import org.jeasy.jobs.request.JobRequest;
import org.jeasy.jobs.request.JobRequestRepository;
import org.jeasy.jobs.request.JobRequestStatus;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.sql.DataSource;
import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@org.springframework.test.context.ContextConfiguration(classes = {ContextConfiguration.class})
public abstract class AbstractJobRequestRepositoryTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobRequestRepository jobRequestRepository;

    private JdbcTemplate jdbcTemplate;

    @BeforeClass
    public static void init() throws Exception {
        File file = new File("src/test/resources/database.properties");
        System.setProperty(DataSourceConfiguration.DATA_SOURCE_CONFIGURATION_PROPERTY, file.getAbsolutePath());
    }

    @Before
    public void setUp() throws Exception {
        Resource resource = new ClassPathResource("database-schema.sql");
        ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator(resource);
        databasePopulator.execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void testJobRequestPersistence() throws Exception {
        // given
        jobRepository.save(new Job(1, "MyJob"));

        // when
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.PENDING, LocalDateTime.now(), null));

        // then
        Integer nbJobRequests = jdbcTemplate.queryForObject("select count(*) from job_request", Integer.class);
        assertThat(nbJobRequests).isEqualTo(1);
    }

    public void testGetPendingJobRequests() throws Exception {
        // given
        jobRepository.save(new Job(1, "MyJob"));
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.PENDING, LocalDateTime.now(), null));
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.PENDING, LocalDateTime.now(), null));
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.SUBMITTED, LocalDateTime.now(), null));
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.PROCESSED, LocalDateTime.now(), LocalDateTime.now().plus(2, ChronoUnit.MINUTES)));

        // when
        List<JobRequest> pendingJobRequests = jobRequestRepository.getPendingJobRequests();

        // then
        assertThat(pendingJobRequests.size()).isEqualTo(2);
    }

    public void testFindAllJobRequests() throws Exception {
        // given
        jobRepository.save(new Job(1, "MyJob"));
        jobRequestRepository.save(new JobRequest(1, "x=1", JobRequestStatus.PENDING, LocalDateTime.now(), null));
        jobRequestRepository.save(new JobRequest(1, "x=2", JobRequestStatus.PENDING, LocalDateTime.now(), null));

        // when
        List<JobRequest> jobRequests = jobRequestRepository.findAllJobRequests();

        // then
        assertThat(jobRequests.size()).isEqualTo(2);
    }

    public void testUpdateJobRequestStatus() throws Exception {
        // given
        jobRepository.save(new Job(1, "MyJob"));
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.PENDING, LocalDateTime.now(), null));
        List<JobRequest> pendingJobRequests = jobRequestRepository.getPendingJobRequests();
        JobRequest jobRequest = pendingJobRequests.get(0);

        // when
        jobRequestRepository.updateStatus(jobRequest.getId(), JobRequestStatus.SUBMITTED);

        // then
        JobRequest request = jobRequestRepository.getById(jobRequest.getId());
        assertThat(request.getStatus()).isEqualTo(JobRequestStatus.SUBMITTED);
    }

    public void testUpdateJobRequestStatusAndProcessingDate() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime processingDate = now.plus(2, ChronoUnit.MINUTES);
        jobRepository.save(new Job(1, "MyJob"));
        jobRequestRepository.save(new JobRequest(1, "", JobRequestStatus.PENDING, now, null));
        List<JobRequest> pendingJobRequests = jobRequestRepository.getPendingJobRequests();
        JobRequest jobRequest = pendingJobRequests.get(0);

        // when
        jobRequestRepository.updateStatusAndProcessingDate(jobRequest.getId(), JobRequestStatus.PROCESSED, processingDate);

        // then
        JobRequest request = jobRequestRepository.getById(jobRequest.getId());
        assertThat(request.getStatus()).isEqualTo(JobRequestStatus.PROCESSED);
        assertThat(request.getProcessingDate()).isEqualToIgnoringSeconds(processingDate); // sometimes this test fails when ignoring only nanoseconds
    }
}

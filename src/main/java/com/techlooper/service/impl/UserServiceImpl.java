package com.techlooper.service.impl;

import com.couchbase.client.CouchbaseClient;
import com.techlooper.entity.UserEntity;
import com.techlooper.entity.UserRegistration;
import com.techlooper.entity.VnwUserProfile;
import com.techlooper.entity.userimport.UserImportEntity;
import com.techlooper.model.*;
import com.techlooper.repository.couchbase.UserRegistrationRepository;
import com.techlooper.repository.couchbase.UserRepository;
import com.techlooper.repository.talentsearch.query.TalentSearchQuery;
import com.techlooper.repository.userimport.UserImportRepository;
import com.techlooper.service.TalentSearchDataProcessor;
import com.techlooper.service.UserEvaluationService;
import com.techlooper.service.UserService;
import com.techlooper.service.VietnamWorksUserService;
import org.dozer.Mapper;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryFilterBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.jasypt.util.text.TextEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.FacetedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

/**
 * Created by NguyenDangKhoa on 12/11/14.
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    @Resource
    private UserRepository userRepository;

    @Resource
    private UserImportRepository userImportRepository;

    @Resource
    private UserRegistrationRepository userRegistrationRepository;

    @Resource
    private Mapper dozerMapper;

    @Resource
    private TextEncryptor textEncryptor;

    @Resource
    private VietnamWorksUserService vietnamworksUserService;

    @Resource
    private UserEvaluationService userEvaluationService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private CouchbaseClient couchbaseClient;

    public void save(UserEntity userEntity) {
        userRepository.save(userEntity);
    }

    public void save(UserInfo userInfo) {
        UserEntity userEntity = userRepository.findOne(userInfo.getId());
        dozerMapper.map(userInfo, userEntity);
        userRepository.save(userEntity);
    }

    public UserEntity findById(String id) {
        return userRepository.findOne(id);
    }

    public UserInfo findUserInfoByKey(String key) {
        return dozerMapper.map(findUserEntityByKey(key), UserInfo.class);
    }

    public UserEntity findUserEntityByKey(String key) {
        String emailAddress = textEncryptor.decrypt(key);
        return userRepository.findOne(emailAddress);
    }

    public boolean verifyVietnamworksAccount(UserEntity userEntity) {
        boolean result = vietnamworksUserService.existUser(userEntity.getEmailAddress());
        if (result) {
            userEntity.getProfiles().put(SocialProvider.VIETNAMWORKS, null);
        }
        return result;
    }

    public boolean registerVietnamworksAccount(UserInfo userInfo) {
        boolean registerSuccess = false;
        if (userInfo.acceptRegisterVietnamworksAccount() &&
                !(registerSuccess = vietnamworksUserService.register(dozerMapper.map(userInfo, VnwUserProfile.class)))) {
            userInfo.removeProfile(SocialProvider.VIETNAMWORKS);
        }
        return registerSuccess;
    }

    public boolean addCrawledUser(UserImportEntity userImportData, SocialProvider socialProvider) {
        UserImportEntity userImportEntity = findUserImportByEmail(userImportData.getEmail());

        if (userImportEntity != null) {
            userImportEntity.withProfile(socialProvider, userImportData.getProfiles().get(socialProvider));
        } else {
            userImportEntity = dozerMapper.map(userImportData, UserImportEntity.class);
            userImportEntity.withProfile(socialProvider, userImportData.getProfiles().get(socialProvider));
            userImportEntity.setCrawled(true);
        }

        return userImportRepository.save(userImportEntity) != null;
    }

    public int addCrawledUserAll(List<UserImportEntity> users, SocialProvider socialProvider, UpdateModeEnum updateMode) {
        List<UserImportEntity> shouldBeSavedUsers = new ArrayList<>();

        for (UserImportEntity user : users) {
            try {
                UserImportEntity userImportEntity = findUserImportByEmail(user.getEmail());

                switch (updateMode) {
                    case ADD_NEW:
                        if (userImportEntity == null) {
                            shouldBeSavedUsers.add(user);
                        }
                        break;
                    case OVERWRITE:
                        if (userImportEntity != null) {
                            userImportEntity.withProfile(socialProvider, user.getProfiles().get(socialProvider));
                            shouldBeSavedUsers.add(userImportEntity);
                        } else {
                            shouldBeSavedUsers.add(user);
                        }
                    default: //MERGE
                        if (userImportEntity != null) {
                            userImportEntity.mergeProfile(socialProvider, user.getProfiles().get(socialProvider));
                            shouldBeSavedUsers.add(userImportEntity);
                        } else {
                            shouldBeSavedUsers.add(user);
                        }
                }
            } catch (Exception ex) {
                LOGGER.error("User Import Fail : " + user.getEmail(), ex);
            }
        }

        return Lists.newArrayList(userImportRepository.save(shouldBeSavedUsers)).size();
    }

    public UserImportEntity findUserImportByEmail(String email) {
        UserImportEntity userImportEntity = userImportRepository.findOne(email);

        if (userImportEntity != null) {
            return userImportEntity;
        } else {
            QueryFilterBuilder queryFilterBuilder = FilterBuilders.queryFilter(
                    QueryBuilders.queryString(email).defaultOperator(QueryStringQueryBuilder.Operator.AND)).cache(true);
            SearchQuery userSearchQuery = new NativeSearchQueryBuilder()
                    .withFilter(queryFilterBuilder)
                    .withPageable(new PageRequest(0, 10))
                    .build();
            Page<UserImportEntity> result = userImportRepository.search(userSearchQuery);
            if (result.getNumberOfElements() > 0) {
                return result.getContent().get(0);
            } else {
                return null;
            }
        }

    }

    @Override
    public List<UserImportEntity> getAll(int pageNumber, int pageSize) {
        final SearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQuery()
                        .mustNot(wildcardQuery("email", "*users.noreply.github.com"))
                        .mustNot(wildcardQuery("email", "*missing.com")))
                .withPageable(new PageRequest(pageNumber, pageSize))
                .build();

        return userImportRepository.search(searchQuery).getContent();
    }

    @Override
    public TalentSearchResponse findTalent(TalentSearchRequest param) {
        List<SocialProvider> socialProviders = Arrays.asList(SocialProvider.GITHUB);
        TalentSearchResponse.Builder builder = new TalentSearchResponse.Builder();

        for(SocialProvider socialProvider : socialProviders) {
            TalentSearchQuery talentSearchQuery =
                    applicationContext.getBean(socialProvider + "TalentSearchQuery", TalentSearchQuery.class);
            ElasticsearchRepository talentSearchRepository =
                    applicationContext.getBean(socialProvider + "TalentSearchRepository", ElasticsearchRepository.class);
            TalentSearchDataProcessor talentSearchDataProcessor =
                    applicationContext.getBean(socialProvider + "TalentSearchDataProcessor", TalentSearchDataProcessor.class);

            if (param != null) {
                talentSearchDataProcessor.normalizeInputParameter(param);
            } else {
                param = talentSearchDataProcessor.getSearchAllRequestParameter();
            }

            FacetedPage<UserImportEntity> pageResult = talentSearchRepository.search(talentSearchQuery.getSearchQuery(param));
            builder.withTotal(pageResult.getTotalElements());
            builder.withResult(talentSearchDataProcessor.process(pageResult.getContent()));
        }

        return builder.build();
    }

    public void registerUser(UserInfo userInfo) {
        UserRegistration user = dozerMapper.map(userInfo, UserRegistration.class);
        user.setId(userInfo.getEmailAddress());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        user.setCreatedDateTime(sdf.format(new Date()));
        userRegistrationRepository.save(user);
    }

    public long countRegisteredUser() {
        return userRegistrationRepository.count();
    }

}

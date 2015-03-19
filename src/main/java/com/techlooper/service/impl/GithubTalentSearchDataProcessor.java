package com.techlooper.service.impl;

import com.techlooper.entity.userimport.UserImportEntity;
import com.techlooper.model.SocialProvider;
import com.techlooper.model.Talent;
import com.techlooper.model.TalentSearchRequest;
import com.techlooper.repository.JsonConfigRepository;
import com.techlooper.service.TalentSearchDataProcessor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by NguyenDangKhoa on 3/11/15.
 */
@Service("GITHUBTalentSearchDataProcessor")
public class GithubTalentSearchDataProcessor implements TalentSearchDataProcessor {

    @Resource
    private JsonConfigRepository jsonConfigRepository;

    @Override
    public List<Talent> process(List<UserImportEntity> users) {
        return users.stream().map(userImportEntity -> {
            Map<String, Object> profile = (Map<String, Object>) userImportEntity.getProfiles().get(SocialProvider.GITHUB);

            if (profile == null) {
                return null;
            }

            Talent.Builder talentBuilder = new Talent.Builder();
            String username = StringUtils.trimToEmpty((String) profile.get("username"));
            String description = StringUtils.trimToEmpty((String) profile.get("description"))
                    .replace(username, "")
                    .replace(" Follow their code on GitHub.", "")
                    .trim();
            String imageUrl = StringUtils.isNotEmpty((String)profile.get("imageurl")) ?
                    StringUtils.trimToEmpty((String)profile.get("imageurl")) : StringUtils.trimToEmpty((String)profile.get("imageUrl"));

            talentBuilder.withEmail(userImportEntity.getEmail())
                    .withUsername(username)
                    .withFullName(StringUtils.trimToEmpty((String) userImportEntity.getFullName()))
                    .withImageUrl(imageUrl)
                    .withCompany(StringUtils.trimToEmpty((String) profile.get("company")))
                    .withDescription(description)
                    .withLocation(StringUtils.trimToEmpty((String) profile.get("location")))
                    .withJobTitle("")
                    .withSkills(((List<String>) profile.get("skills")));

            Map<String,Integer> scoreMap = jsonConfigRepository.getGithubTalentScore();
            int totalScore = 0;
            for(Map.Entry<String,Integer> entry : scoreMap.entrySet()) {
                Object profileItem = profile.get(entry.getKey());
                int profileItemUnitScore = entry.getValue();
                if (profileItem instanceof List) {
                    totalScore += profileItemUnitScore * ((List)profileItem).size();
                } else if (profileItem != null) {
                    totalScore += profileItemUnitScore;
                }
            }

            talentBuilder.withScore(totalScore);
            return talentBuilder.build();
        }).filter(talent -> talent != null).collect(Collectors.toList());
    }

    @Override
    public void normalizeInputParameter(TalentSearchRequest param) {
        param.getSkills().removeAll(Arrays.asList(null, ""));
        param.getLocations().removeAll(Arrays.asList(null, ""));
        param.getCompanies().removeAll(Arrays.asList(null, ""));
    }

}

package org.example.connectcg_be.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.connectcg_be.cache.HobbyCache;
import org.example.connectcg_be.dto.HobbyDTO;
import org.example.connectcg_be.repository.HobbyRepository;
import org.example.connectcg_be.service.HobbyService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HobbyServiceImpl implements HobbyService {
    private final HobbyRepository hobbyRepository;
    private final HobbyCache hobbyCache;

    @Override
    public List<HobbyDTO> getAllHobbies() {
        return hobbyCache.find().orElseGet(() -> {
            List<HobbyDTO> hobbies = hobbyRepository.findAll().stream()
                    .map(hobby -> HobbyDTO.builder()
                        .id(hobby.getId())
                        .code(hobby.getCode())
                        .name(hobby.getName())
                        .icon(hobby.getIcon())
                        .category(hobby.getCategory())
                        .build())
                    .toList();
            hobbyCache.store(hobbies);
            return hobbies;
        });
    }
}

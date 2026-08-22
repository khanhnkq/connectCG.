package org.example.connectcg_be.service;

import org.example.connectcg_be.cache.HobbyCache;
import org.example.connectcg_be.dto.HobbyDTO;
import org.example.connectcg_be.entity.Hobby;
import org.example.connectcg_be.repository.HobbyRepository;
import org.example.connectcg_be.service.impl.HobbyServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HobbyServiceImplTest {
    @Test
    void cacheHitSkipsDatabase() {
        HobbyRepository repository = mock(HobbyRepository.class);
        HobbyCache cache = mock(HobbyCache.class);
        HobbyDTO cached = HobbyDTO.builder().id(1).code("ART").name("Art").build();
        when(cache.find()).thenReturn(Optional.of(List.of(cached)));
        HobbyServiceImpl service = new HobbyServiceImpl(repository, cache);

        List<HobbyDTO> result = service.getAllHobbies();

        assertEquals("ART", result.get(0).getCode());
        verify(repository, never()).findAll();
    }

    @Test
    void cacheMissLoadsDatabaseAndStoresResult() {
        HobbyRepository repository = mock(HobbyRepository.class);
        HobbyCache cache = mock(HobbyCache.class);
        when(cache.find()).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of(new Hobby(1, "ART", "Art", null, "CREATIVE")));
        HobbyServiceImpl service = new HobbyServiceImpl(repository, cache);

        List<HobbyDTO> result = service.getAllHobbies();

        assertEquals("Art", result.get(0).getName());
        verify(cache).store(result);
    }
}

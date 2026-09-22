package com.example.ticktetapi.service;

import com.example.ticktetapi.dto.EventoRequestDTO;
import com.example.ticktetapi.exception.RecursoNaoEncontradoException;
import com.example.ticktetapi.exception.RegraNegocioException;
import com.example.ticktetapi.model.Evento;
import com.example.ticktetapi.repository.EventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    @Mock
    private EventoRepository eventoRepository;

    @InjectMocks
    private EventoService eventoService;

    private EventoRequestDTO dto;
    private Evento evento;

    @BeforeEach
    void setUp() {
        dto = new EventoRequestDTO();
        dto.setNome("Show de Rock");
        dto.setDescricao("Show de rock ao vivo");
        dto.setDataHora(LocalDateTime.now().plusDays(10));
        dto.setLocal("Arena Central");
        dto.setPreco(new BigDecimal("150.00"));
        dto.setQuantidadeTotal(100);

        evento = Evento.builder()
                .id(1L)
                .nome("Show de Rock")
                .descricao("Show de rock ao vivo")
                .dataHora(LocalDateTime.now().plusDays(10))
                .local("Arena Central")
                .preco(new BigDecimal("150.00"))
                .quantidadeTotal(100)
                .quantidadeDisponivel(100)
                .build();
    }

    @Test
    void criar_DeveSalvarEventoComQuantidadeDisponivelIgualATotal() {
        when(eventoRepository.save(any(Evento.class))).thenReturn(evento);

        Evento resultado = eventoService.criar(dto);

        assertNotNull(resultado);
        assertEquals(dto.getNome(), resultado.getNome());
        assertEquals(dto.getQuantidadeTotal(), resultado.getQuantidadeTotal());
        assertEquals(dto.getQuantidadeTotal(), resultado.getQuantidadeDisponivel());

        verify(eventoRepository, times(1)).save(any(Evento.class));
    }

    @Test
    void listarTodos_DeveRetornarListaDeEventos() {
        when(eventoRepository.findAll()).thenReturn(List.of(evento));

        List<Evento> resultado = eventoService.listarTodos();

        assertEquals(1, resultado.size());
        assertEquals(evento.getNome(), resultado.get(0).getNome());
        verify(eventoRepository, times(1)).findAll();
    }

    @Test
    void listarTodos_QuandoNaoHaEventos_DeveRetornarListaVazia() {
        when(eventoRepository.findAll()).thenReturn(List.of());

        List<Evento> resultado = eventoService.listarTodos();

        assertTrue(resultado.isEmpty());
        verify(eventoRepository, times(1)).findAll();
    }

    @Test
    void buscarPorId_QuandoExiste_DeveRetornarEvento() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        Evento resultado = eventoService.buscarPorId(1L);

        assertEquals(evento.getId(), resultado.getId());
        verify(eventoRepository, times(1)).findById(1L);
    }

    @Test
    void buscarPorId_QuandoNaoExiste_DeveLancarRecursoNaoEncontradoException() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException exception = assertThrows(
                RecursoNaoEncontradoException.class,
                () -> eventoService.buscarPorId(99L)
        );

        assertTrue(exception.getMessage().contains("99"));
        verify(eventoRepository, times(1)).findById(99L);
    }

    @Test
    void atualizar_ComSucesso_DeveAtualizarCamposEQuantidadeDisponivel() {
        // Evento já vendeu 20 ingressos (100 total, 80 disponíveis)
        evento.setQuantidadeDisponivel(80);

        EventoRequestDTO dtoAtualizacao = new EventoRequestDTO();
        dtoAtualizacao.setNome("Show de Rock - Atualizado");
        dtoAtualizacao.setDescricao("Nova descrição");
        dtoAtualizacao.setDataHora(evento.getDataHora());
        dtoAtualizacao.setLocal("Novo Local");
        dtoAtualizacao.setPreco(new BigDecimal("180.00"));
        dtoAtualizacao.setQuantidadeTotal(150); // aumentando de 100 para 150

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Evento resultado = eventoService.atualizar(1L, dtoAtualizacao);

        // 20 ingressos vendidos, nova quantidade total 150 -> disponível deve ser 130
        assertEquals("Show de Rock - Atualizado", resultado.getNome());
        assertEquals("Novo Local", resultado.getLocal());
        assertEquals(150, resultado.getQuantidadeTotal());
        assertEquals(130, resultado.getQuantidadeDisponivel());

        verify(eventoRepository, times(1)).findById(1L);
        verify(eventoRepository, times(1)).save(any(Evento.class));
    }

    @Test
    void atualizar_QuandoNovaQuantidadeMenorQueVendida_DeveLancarRegraNegocioException() {
        // 100 total, 20 disponíveis -> 80 já vendidos
        evento.setQuantidadeDisponivel(20);

        EventoRequestDTO dtoAtualizacao = new EventoRequestDTO();
        dtoAtualizacao.setQuantidadeTotal(50); // menor que os 80 já vendidos

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThrows(RegraNegocioException.class,
                () -> eventoService.atualizar(1L, dtoAtualizacao));

        verify(eventoRepository, times(1)).findById(1L);
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void atualizar_QuandoEventoNaoExiste_DeveLancarRecursoNaoEncontradoException() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> eventoService.atualizar(99L, dto));

        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void deletar_ComSucesso_DeveChamarRepositoryDelete() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        doNothing().when(eventoRepository).delete(evento);

        eventoService.deletar(1L);

        verify(eventoRepository, times(1)).findById(1L);
        verify(eventoRepository, times(1)).delete(evento);
    }

    @Test
    void deletar_QuandoEventoNaoExiste_DeveLancarRecursoNaoEncontradoException() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> eventoService.deletar(99L));

        verify(eventoRepository, never()).delete(any(Evento.class));
    }
}

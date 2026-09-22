package com.example.ticktetapi.service;

import com.example.ticktetapi.dto.CompraIngressoRequestDTO;
import com.example.ticktetapi.exception.RecursoNaoEncontradoException;
import com.example.ticktetapi.exception.RegraNegocioException;
import com.example.ticktetapi.model.Cliente;
import com.example.ticktetapi.model.Evento;
import com.example.ticktetapi.model.Ingresso;
import com.example.ticktetapi.model.StatusIngresso;
import com.example.ticktetapi.repository.ClienteRepository;
import com.example.ticktetapi.repository.EventoRepository;
import com.example.ticktetapi.repository.IngressoRepository;
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
class IngressoServiceTest {

    @Mock
    private IngressoRepository ingressoRepository;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private IngressoService ingressoService;

    private Evento evento;
    private Cliente cliente;
    private CompraIngressoRequestDTO dtoCompra;

    @BeforeEach
    void setUp() {
        evento = Evento.builder()
                .id(1L)
                .nome("Show de Rock")
                .dataHora(LocalDateTime.now().plusDays(10))
                .local("Arena Central")
                .preco(new BigDecimal("150.00"))
                .quantidadeTotal(100)
                .quantidadeDisponivel(10)
                .build();

        cliente = Cliente.builder()
                .id(1L)
                .build();

        dtoCompra = new CompraIngressoRequestDTO();
        dtoCompra.setEventoId(1L);
        dtoCompra.setClienteId(1L);
        dtoCompra.setQuantidade(2);
    }

    // ---------- comprar ----------

    @Test
    void comprar_ComSucesso_DeveGerarIngressosEDecrementarQuantidadeDisponivel() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Ingresso> resultado = ingressoService.comprar(dtoCompra);

        assertEquals(2, resultado.size());
        resultado.forEach(ingresso -> {
            assertEquals(StatusIngresso.ATIVO, ingresso.getStatus());
            assertEquals(evento.getPreco(), ingresso.getValorPago());
            assertNotNull(ingresso.getCodigo());
        });
        assertEquals(8, evento.getQuantidadeDisponivel()); // 10 - 2

        verify(ingressoRepository, times(2)).save(any(Ingresso.class));
        verify(eventoRepository, times(1)).save(evento);
    }

    @Test
    void comprar_QuandoQuantidadeNaoInformada_DeveComprarUmIngresso() {
        dtoCompra.setQuantidade(null);

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Ingresso> resultado = ingressoService.comprar(dtoCompra);

        assertEquals(1, resultado.size());
        assertEquals(9, evento.getQuantidadeDisponivel()); // 10 - 1
    }

    @Test
    void comprar_QuandoEventoNaoExiste_DeveLancarRecursoNaoEncontradoException() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> ingressoService.comprar(dtoCompra));

        verify(clienteRepository, never()).findById(any());
        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void comprar_QuandoClienteNaoExiste_DeveLancarRecursoNaoEncontradoException() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> ingressoService.comprar(dtoCompra));

        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void comprar_QuandoEventoJaOcorreu_DeveLancarRegraNegocioException() {
        evento.setDataHora(LocalDateTime.now().minusDays(1));

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        assertThrows(RegraNegocioException.class,
                () -> ingressoService.comprar(dtoCompra));

        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void comprar_QuandoIngressosEsgotados_DeveLancarRegraNegocioException() {
        evento.setQuantidadeDisponivel(1);
        dtoCompra.setQuantidade(2);

        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        RegraNegocioException exception = assertThrows(RegraNegocioException.class,
                () -> ingressoService.comprar(dtoCompra));

        assertTrue(exception.getMessage().contains("esgotados"));
        verify(ingressoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
    }

    // ---------- listarTodos ----------

    @Test
    void listarTodos_DeveRetornarListaDeIngressos() {
        Ingresso ingresso = Ingresso.builder().id(1L).status(StatusIngresso.ATIVO).build();
        when(ingressoRepository.findAll()).thenReturn(List.of(ingresso));

        List<Ingresso> resultado = ingressoService.listarTodos();

        assertEquals(1, resultado.size());
        verify(ingressoRepository, times(1)).findAll();
    }

    // ---------- buscarPorId ----------

    @Test
    void buscarPorId_QuandoExiste_DeveRetornarIngresso() {
        Ingresso ingresso = Ingresso.builder().id(1L).status(StatusIngresso.ATIVO).build();
        when(ingressoRepository.findById(1L)).thenReturn(Optional.of(ingresso));

        Ingresso resultado = ingressoService.buscarPorId(1L);

        assertEquals(1L, resultado.getId());
        verify(ingressoRepository, times(1)).findById(1L);
    }

    @Test
    void buscarPorId_QuandoNaoExiste_DeveLancarRecursoNaoEncontradoException() {
        when(ingressoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> ingressoService.buscarPorId(99L));
    }

    // ---------- listarPorEvento / listarPorCliente ----------

    @Test
    void listarPorEvento_DeveRetornarIngressosDoEvento() {
        Ingresso ingresso = Ingresso.builder().id(1L).evento(evento).build();
        when(ingressoRepository.findByEventoId(1L)).thenReturn(List.of(ingresso));

        List<Ingresso> resultado = ingressoService.listarPorEvento(1L);

        assertEquals(1, resultado.size());
        verify(ingressoRepository, times(1)).findByEventoId(1L);
    }

    @Test
    void listarPorCliente_DeveRetornarIngressosDoCliente() {
        Ingresso ingresso = Ingresso.builder().id(1L).cliente(cliente).build();
        when(ingressoRepository.findByClienteId(1L)).thenReturn(List.of(ingresso));

        List<Ingresso> resultado = ingressoService.listarPorCliente(1L);

        assertEquals(1, resultado.size());
        verify(ingressoRepository, times(1)).findByClienteId(1L);
    }

    // ---------- cancelar ----------

    @Test
    void cancelar_ComSucesso_DeveAlterarStatusEIncrementarQuantidadeDisponivel() {
        Ingresso ingresso = Ingresso.builder()
                .id(1L)
                .status(StatusIngresso.ATIVO)
                .evento(evento)
                .build();

        when(ingressoRepository.findById(1L)).thenReturn(Optional.of(ingresso));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ingresso resultado = ingressoService.cancelar(1L);

        assertEquals(StatusIngresso.CANCELADO, resultado.getStatus());
        assertEquals(11, evento.getQuantidadeDisponivel()); // 10 + 1

        verify(ingressoRepository, times(1)).save(ingresso);
        verify(eventoRepository, times(1)).save(evento);
    }

    @Test
    void cancelar_QuandoJaCancelado_DeveLancarRegraNegocioException() {
        Ingresso ingresso = Ingresso.builder()
                .id(1L)
                .status(StatusIngresso.CANCELADO)
                .evento(evento)
                .build();

        when(ingressoRepository.findById(1L)).thenReturn(Optional.of(ingresso));

        assertThrows(RegraNegocioException.class,
                () -> ingressoService.cancelar(1L));

        verify(ingressoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void cancelar_QuandoUtilizado_DeveLancarRegraNegocioException() {
        Ingresso ingresso = Ingresso.builder()
                .id(1L)
                .status(StatusIngresso.UTILIZADO)
                .evento(evento)
                .build();

        when(ingressoRepository.findById(1L)).thenReturn(Optional.of(ingresso));

        assertThrows(RegraNegocioException.class,
                () -> ingressoService.cancelar(1L));

        verify(ingressoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
    }

    // ---------- marcarComoUtilizado ----------

    @Test
    void marcarComoUtilizado_ComSucesso_DeveAlterarStatusParaUtilizado() {
        Ingresso ingresso = Ingresso.builder()
                .id(1L)
                .status(StatusIngresso.ATIVO)
                .build();

        when(ingressoRepository.findById(1L)).thenReturn(Optional.of(ingresso));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ingresso resultado = ingressoService.marcarComoUtilizado(1L);

        assertEquals(StatusIngresso.UTILIZADO, resultado.getStatus());
        verify(ingressoRepository, times(1)).save(ingresso);
    }

    @Test
    void marcarComoUtilizado_QuandoNaoAtivo_DeveLancarRegraNegocioException() {
        Ingresso ingresso = Ingresso.builder()
                .id(1L)
                .status(StatusIngresso.CANCELADO)
                .build();

        when(ingressoRepository.findById(1L)).thenReturn(Optional.of(ingresso));

        assertThrows(RegraNegocioException.class,
                () -> ingressoService.marcarComoUtilizado(1L));

        verify(ingressoRepository, never()).save(any());
    }
}

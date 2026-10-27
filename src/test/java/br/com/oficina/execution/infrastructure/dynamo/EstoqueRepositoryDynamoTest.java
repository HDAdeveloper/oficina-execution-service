package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.model.ItemEstoque;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EstoqueRepositoryDynamoTest extends DynamoTestBase {

    @Autowired EstoqueRepository estoque;

    private static String codigo() {
        return "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void criaEBuscaUmaPeca() {
        String c = codigo();
        estoque.criar(new ItemEstoque(c, "Filtro de óleo", 12));

        ItemEstoque lido = estoque.buscar(c).orElseThrow();
        assertThat(lido.nome()).isEqualTo("Filtro de óleo");
        assertThat(lido.disponivel()).isEqualTo(12);
    }

    @Test
    void buscarPecaInexistenteDevolveVazio() {
        assertThat(estoque.buscar("NAO-EXISTE-" + codigo())).isEmpty();
    }

    @Test
    void naoCriaDuasVezesOMesmoCodigo() {
        String c = codigo();
        estoque.criar(new ItemEstoque(c, "Filtro", 1));

        assertThatThrownBy(() -> estoque.criar(new ItemEstoque(c, "Outro", 9))).isInstanceOf(ConflitoException.class);
        assertThat(estoque.buscar(c).orElseThrow().disponivel()).isEqualTo(1); // não sobrescreveu
    }

    @Test
    void reporSomaAtomicamente() {
        String c = codigo();
        estoque.criar(new ItemEstoque(c, "Filtro", 5));

        assertThat(estoque.repor(c, 3).disponivel()).isEqualTo(8);
        assertThat(estoque.buscar(c).orElseThrow().disponivel()).isEqualTo(8);
    }

    @Test
    void reporPecaInexistenteNaoCriaARepeca() {
        String c = codigo();

        assertThatThrownBy(() -> estoque.repor(c, 3)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(estoque.buscar(c)).isEmpty();
    }

    @Test
    void buscaVariosIgnorandoOsInexistentesEOsRepetidos() {
        String a = codigo();
        String b = codigo();
        estoque.criar(new ItemEstoque(a, "A", 1));
        estoque.criar(new ItemEstoque(b, "B", 2));

        List<ItemEstoque> itens = estoque.buscarVarios(List.of(a, b, a, "NAO-" + codigo()));

        assertThat(itens).extracting(ItemEstoque::codigo).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void listaTodasAsPecas() {
        String c = codigo();
        estoque.criar(new ItemEstoque(c, "Listada", 3));

        assertThat(estoque.listar()).extracting(ItemEstoque::codigo).contains(c);
    }
}

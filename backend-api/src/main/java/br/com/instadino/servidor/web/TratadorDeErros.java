package br.com.instadino.servidor.web;

import br.com.instadino.contratos.conta.ErroApi;
import br.com.instadino.servidor.servico.ErroDeNegocio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Respostas de erro uniformes, sem pilha de execução, caminhos ou detalhes internos. */
@RestControllerAdvice
class TratadorDeErros {

    private static final System.Logger LOG = System.getLogger(TratadorDeErros.class.getName());

    @ExceptionHandler(ErroDeNegocio.class)
    ResponseEntity<ErroApi> negocio(ErroDeNegocio e) {
        return ResponseEntity.status(e.status()).body(new ErroApi(e.codigo(), e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ErroApi> requisicaoInvalida(Exception e) {
        return ResponseEntity.badRequest().body(new ErroApi("REQUISICAO_INVALIDA", "A requisição está malformada ou incompleta."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErroApi> metodo(Exception e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErroApi("METODO_NAO_PERMITIDO", "Método não permitido."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErroApi> tipo(Exception e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErroApi("TIPO_NAO_SUPORTADO", "Use application/json."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErroApi> naoEncontrado(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErroApi("NAO_ENCONTRADO", "Recurso não encontrado."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroApi> inesperado(Exception e) {
        LOG.log(System.Logger.Level.ERROR, "Erro interno ({0}).", e.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErroApi("ERRO_INTERNO", "Ocorreu um erro interno. Tente novamente mais tarde."));
    }
}

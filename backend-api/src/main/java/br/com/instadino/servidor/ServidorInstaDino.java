package br.com.instadino.servidor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Backend comercial do InstaDino: contas, sessões, licenças assinadas e pagamentos.
 * Este serviço NUNCA recebe listas de seguidores, mensagens, mídias nem arquivos ZIP da exportação.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories(considerNestedRepositories = true)
public class ServidorInstaDino {

    public static void main(String[] args) {
        SpringApplication.run(ServidorInstaDino.class, args);
    }
}

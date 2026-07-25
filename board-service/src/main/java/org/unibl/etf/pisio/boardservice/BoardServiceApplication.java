package org.unibl.etf.pisio.boardservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BoardServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(BoardServiceApplication.class, args);
    }

}

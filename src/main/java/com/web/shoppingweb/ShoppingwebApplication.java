package com.web.shoppingweb;

// import org.springframework.context.annotation.Bean;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShoppingwebApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShoppingwebApplication.class, args);
	}
    // @Bean
    // public CommandLineRunner printHash() {
    //     return args -> {
    //         BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    //         System.out.println("HASH=" + encoder.encode("123456"));
    //     };
    // }
}

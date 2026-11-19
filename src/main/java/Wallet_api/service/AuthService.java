package Wallet_api.service;

import Wallet_api.dto.LoginRequest;
import Wallet_api.dto.LoginResponse;
import Wallet_api.dto.RegisterRequest;
import Wallet_api.dto.RegisterResponse;
import Wallet_api.entity.Account;
import Wallet_api.entity.User;
import Wallet_api.repository.AccountRepository;
import Wallet_api.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phoneNumber = request.phoneNumber().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email is already registered"
            );
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This phone number is already registered"
            );
        }

        int passwordBytes =
                request.password().getBytes(StandardCharsets.UTF_8).length;

        if (passwordBytes > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is too long for BCrypt"
            );
        }

        User user = new User(
                request.name().trim(),
                email,
                phoneNumber,
                passwordEncoder.encode(request.password())
        );

        User savedUser = userRepository.save(user);
        accountRepository.save(new Account(savedUser));

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                savedUser.getRole()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        Instant now = Instant.now();
        long expiresInSeconds = 3600;

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("wallet-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new LoginResponse(
                "Bearer",
                token,
                expiresInSeconds,
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}

package com.fantasy.lnb.feature.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Búsqueda principal post-login: email es único entre proveedores
    Optional<Usuario> findByEmail(String email);

    // Útil para detectar si un usuario cambió de proveedor con el mismo email
    Optional<Usuario> findByProviderAndProviderId(String provider, String providerId);
}
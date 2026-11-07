package com.fantasy.lnb.scraper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una diferencia con el fixture de la liga que ya se le avisó a los admins. Está en
 * la base para no repetir el aviso cada vez que corre el control o se reinicia el
 * servidor. Se borra cuando la diferencia desaparece.
 */
@Entity
@Table(name = "aviso_fixture")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvisoFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String clave;

    @Column(nullable = false)
    private LocalDateTime avisadoEn;
}

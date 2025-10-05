package com.learning.awsinfra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "entries")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "meta_key", nullable = false)
    private String key;

    @Column(name = "meta_value", nullable = false)
    private String value;
}

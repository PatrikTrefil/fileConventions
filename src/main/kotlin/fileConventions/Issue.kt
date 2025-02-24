package com.trefilsoftware.fileConventions

import jakarta.persistence.*

@Entity
@Table(name = "Issue")
class Issue {
  @Id @Column(name = "id") @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int

  @Column(name = "source_file_path") val sourceFilePath: String

  @ManyToOne @JoinColumn(name = "source_id") val source: Rule

  constructor() {
    id = 0
    source = Rule()
    sourceFilePath = ""
  }

  constructor(source: Rule, sourceFilePath: String) {
    id = 0
    this.source = source
    this.sourceFilePath = sourceFilePath
  }
}

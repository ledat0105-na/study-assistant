package com.example.studyassistant.billing;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Entity @Table(name="monthly_feature_usage", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","period_start","feature"}))
@Getter @Setter @NoArgsConstructor
public class MonthlyUsage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="user_id",nullable=false) private Long userId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",insertable=false,updatable=false)
 @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE)
 private com.example.studyassistant.entity.User user;
 @Column(name="period_start",nullable=false) private LocalDate periodStart;
 @Column(nullable=false,length=24) private String feature;
 @Column(nullable=false) private long used;
}

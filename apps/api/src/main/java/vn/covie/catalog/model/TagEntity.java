package vn.covie.catalog.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="tags") public class TagEntity {
 @Id private UUID id; @Column(nullable=false,length=100) private String name; @Column(name="normalized_name",nullable=false,length=100) private String normalizedName; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected TagEntity(){} public static TagEntity create(UUID id,String name,String normalizedName,Instant createdAt){TagEntity tag=new TagEntity();tag.id=id;tag.name=name;tag.normalizedName=normalizedName;tag.createdAt=createdAt;return tag;} public UUID getId(){return id;} public String getName(){return name;} public String getNormalizedName(){return normalizedName;} public Instant getCreatedAt(){return createdAt;}
}

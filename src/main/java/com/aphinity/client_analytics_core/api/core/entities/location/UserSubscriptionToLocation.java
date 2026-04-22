package com.aphinity.client_analytics_core.api.core.entities.location;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "user_subscription_to_location",
    uniqueConstraints = @UniqueConstraint(columnNames = {"location_id", "user_email"})
)
public class UserSubscriptionToLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Database-level foreign key uses ON UPDATE CASCADE so subscriptions follow
     * email changes on the owning app_user row.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_email", nullable = false, referencedColumnName = "email")
    private AppUser userEmail;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(AppUser userEmail) {
        this.userEmail = userEmail;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

}
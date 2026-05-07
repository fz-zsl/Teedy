package com.sismics.docs.core.model.jpa;

import com.google.common.base.MoreObjects;
import com.sismics.docs.core.constant.AclType;
import com.sismics.docs.core.constant.PermType;

import jakarta.persistence.*;
import java.util.Date;

/**
 * ACL entity.
 * 
 * @author bgamard
 */
@Entity
@Table(name = "T_ACL")
public class Acl implements Loggable {
    /**
     * ACL ID.
     */
    @Id
    @Column(name = "ACL_ID_C", length = 36)
    private String id;
    
    /**
     * ACL permission.
     */
    @Column(name = "ACL_PERM_C", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private PermType perm;

    /**
     * ACL type.
     */
    @Column(name = "ACL_TYPE_C", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private AclType type;

    /**
     * ACL source ID.
     */
    @Column(name = "ACL_SOURCEID_C", length = 36, nullable = false)
    private String sourceId;
    
    /**
     * ACL target ID.
     */
    @Column(name = "ACL_TARGETID_C", length = 36, nullable = false)
    private String targetId;
    
    /**
     * Deletion date.
     */
    @Column(name = "ACL_DELETEDATE_D")
    private Date deleteDate;
    
    /**
     * Get ACL ID.
     *
     * @return ACL ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set ACL ID.
     *
     * @param id ACL ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get ACL permission.
     *
     * @return ACL permission
     */
    public PermType getPerm() {
        return perm;
    }

    /**
     * Set ACL permission.
     *
     * @param perm ACL permission
     */
    public void setPerm(PermType perm) {
        this.perm = perm;
    }

    /**
     * Get ACL source ID.
     *
     * @return ACL source ID
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Set ACL source ID.
     *
     * @param sourceId ACL source ID
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    /**
     * Get ACL target ID.
     *
     * @return ACL target ID
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * Set ACL target ID.
     *
     * @param targetId ACL target ID
     */
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    /**
     * Get ACL type.
     *
     * @return ACL type
     */
    public AclType getType() {
        return type;
    }

    /**
     * Set ACL type.
     *
     * @param type ACL type
     * @return this Acl
     */
    public Acl setType(AclType type) {
        this.type = type;
        return this;
    }

    @Override
    public Date getDeleteDate() {
        return deleteDate;
    }

    /**
     * Set deletion date.
     *
     * @param deleteDate Deletion date
     */
    public void setDeleteDate(Date deleteDate) {
        this.deleteDate = deleteDate;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("perm", perm)
                .add("sourceId", sourceId)
                .add("targetId", targetId)
                .add("type", type)
                .toString();
    }

    @Override
    public String toMessage() {
        return perm.name();
    }
}

package com.amplifyframework.datastore.generated.model;

import com.amplifyframework.core.model.temporal.Temporal;
import com.amplifyframework.core.model.ModelIdentifier;

import java.util.UUID;
import java.util.Objects;

import androidx.core.util.ObjectsCompat;

import com.amplifyframework.core.model.AuthStrategy;
import com.amplifyframework.core.model.Model;
import com.amplifyframework.core.model.ModelOperation;
import com.amplifyframework.core.model.annotations.AuthRule;
import com.amplifyframework.core.model.annotations.Index;
import com.amplifyframework.core.model.annotations.ModelConfig;
import com.amplifyframework.core.model.annotations.ModelField;
import com.amplifyframework.core.model.query.predicate.QueryField;

import static com.amplifyframework.core.model.query.predicate.QueryField.field;

/** This is an auto generated class representing the NoteData type in your schema. */
@SuppressWarnings("all")
@ModelConfig(pluralName = "NoteData", type = Model.Type.USER, version = 1, authRules = {
  @AuthRule(allow = AuthStrategy.OWNER, ownerField = "owner", identityClaim = "cognito:username", provider = "userPools", operations = { ModelOperation.CREATE, ModelOperation.UPDATE, ModelOperation.DELETE, ModelOperation.READ })
})
@Index(name = "undefined", fields = {"id"})
public final class NoteData implements Model {
  public static final QueryField ID = field("NoteData", "id");
  public static final QueryField NAME = field("NoteData", "name");
  public static final QueryField DESCRIPTION = field("NoteData", "description");
  public static final QueryField IMAGE = field("NoteData", "image");
  public static final QueryField LATITUDE = field("NoteData", "latitude");
  public static final QueryField LONGITUDE = field("NoteData", "longitude");
  private final @ModelField(targetType="ID", isRequired = true) String id;
  private final @ModelField(targetType="String", isRequired = true) String name;
  private final @ModelField(targetType="String") String description;
  private final @ModelField(targetType="String") String image;
  private final @ModelField(targetType="String") String latitude;
  private final @ModelField(targetType="String") String longitude;
  private @ModelField(targetType="AWSDateTime", isReadOnly = true) Temporal.DateTime createdAt;
  private @ModelField(targetType="AWSDateTime", isReadOnly = true) Temporal.DateTime updatedAt;
  /** @deprecated This API is internal to Amplify and should not be used. */
  @Deprecated
   public String resolveIdentifier() {
    return id;
  }

  public String getId() {
      return id;
  }

  public String getName() {
      return name;
  }

  public String getDescription() {
      return description;
  }

  public String getImage() {
      return image;
  }

  public String getLatitude() {
      return latitude;
  }

  public String getLongitude() {
      return longitude;
  }

  public Temporal.DateTime getCreatedAt() {
      return createdAt;
  }

  public Temporal.DateTime getUpdatedAt() {
      return updatedAt;
  }

  private NoteData(String id, String name, String description, String image, String latitude, String longitude) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.image = image;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  @Override
   public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      } else if(obj == null || getClass() != obj.getClass()) {
        return false;
      } else {
      NoteData noteData = (NoteData) obj;
      return ObjectsCompat.equals(getId(), noteData.getId()) &&
              ObjectsCompat.equals(getName(), noteData.getName()) &&
              ObjectsCompat.equals(getDescription(), noteData.getDescription()) &&
              ObjectsCompat.equals(getImage(), noteData.getImage()) &&
              ObjectsCompat.equals(getLatitude(), noteData.getLatitude()) &&
              ObjectsCompat.equals(getLongitude(), noteData.getLongitude()) &&
              ObjectsCompat.equals(getCreatedAt(), noteData.getCreatedAt()) &&
              ObjectsCompat.equals(getUpdatedAt(), noteData.getUpdatedAt());
      }
  }

  @Override
   public int hashCode() {
    return new StringBuilder()
      .append(getId())
      .append(getName())
      .append(getDescription())
      .append(getImage())
      .append(getLatitude())
      .append(getLongitude())
      .append(getCreatedAt())
      .append(getUpdatedAt())
      .toString()
      .hashCode();
  }

  @Override
   public String toString() {
    return new StringBuilder()
      .append("NoteData {")
      .append("id=" + String.valueOf(getId()) + ", ")
      .append("name=" + String.valueOf(getName()) + ", ")
      .append("description=" + String.valueOf(getDescription()) + ", ")
      .append("image=" + String.valueOf(getImage()) + ", ")
      .append("latitude=" + String.valueOf(getLatitude()) + ", ")
      .append("longitude=" + String.valueOf(getLongitude()) + ", ")
      .append("createdAt=" + String.valueOf(getCreatedAt()) + ", ")
      .append("updatedAt=" + String.valueOf(getUpdatedAt()))
      .append("}")
      .toString();
  }

  public static NameStep builder() {
      return new Builder();
  }

  /**
   * WARNING: This method should not be used to build an instance of this object for a CREATE mutation.
   * This is a convenience method to return an instance of the object with only its ID populated
   * to be used in the context of a parameter in a delete mutation or referencing a foreign key
   * in a relationship.
   * @param id the id of the existing item this instance will represent
   * @return an instance of this model with only ID populated
   */
  public static NoteData justId(String id) {
    return new NoteData(
      id,
      null,
      null,
      null,
      null,
      null
    );
  }

  public CopyOfBuilder copyOfBuilder() {
    return new CopyOfBuilder(id,
      name,
      description,
      image,
      latitude,
      longitude);
  }
  public interface NameStep {
    BuildStep name(String name);
  }


  public interface BuildStep {
    NoteData build();
    BuildStep id(String id);
    BuildStep description(String description);
    BuildStep image(String image);
    BuildStep latitude(String latitude);
    BuildStep longitude(String longitude);
  }


  public static class Builder implements NameStep, BuildStep {
    private String id;
    private String name;
    private String description;
    private String image;
    private String latitude;
    private String longitude;
    public Builder() {

    }

    private Builder(String id, String name, String description, String image, String latitude, String longitude) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.image = image;
      this.latitude = latitude;
      this.longitude = longitude;
    }

    @Override
     public NoteData build() {
        String id = this.id != null ? this.id : UUID.randomUUID().toString();

        return new NoteData(
          id,
          name,
          description,
          image,
          latitude,
          longitude);
    }

    @Override
     public BuildStep name(String name) {
        Objects.requireNonNull(name);
        this.name = name;
        return this;
    }

    @Override
     public BuildStep description(String description) {
        this.description = description;
        return this;
    }

    @Override
     public BuildStep image(String image) {
        this.image = image;
        return this;
    }

    @Override
     public BuildStep latitude(String latitude) {
        this.latitude = latitude;
        return this;
    }

    @Override
     public BuildStep longitude(String longitude) {
        this.longitude = longitude;
        return this;
    }

    /**
     * @param id id
     * @return Current Builder instance, for fluent method chaining
     */
    public BuildStep id(String id) {
        this.id = id;
        return this;
    }
  }


  public final class CopyOfBuilder extends Builder {
    private CopyOfBuilder(String id, String name, String description, String image, String latitude, String longitude) {
      super(id, name, description, image, latitude, longitude);
      Objects.requireNonNull(name);
    }

    @Override
     public CopyOfBuilder name(String name) {
      return (CopyOfBuilder) super.name(name);
    }

    @Override
     public CopyOfBuilder description(String description) {
      return (CopyOfBuilder) super.description(description);
    }

    @Override
     public CopyOfBuilder image(String image) {
      return (CopyOfBuilder) super.image(image);
    }

    @Override
     public CopyOfBuilder latitude(String latitude) {
      return (CopyOfBuilder) super.latitude(latitude);
    }

    @Override
     public CopyOfBuilder longitude(String longitude) {
      return (CopyOfBuilder) super.longitude(longitude);
    }
  }


  public static class NoteDataIdentifier extends ModelIdentifier<NoteData> {
    private static final long serialVersionUID = 1L;
    public NoteDataIdentifier(String id) {
      super(id);
    }
  }

}

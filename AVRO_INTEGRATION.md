# Avro Schemas Integration

The `app-backend` service uses Avro schemas from the `avro-schemas` repository for data serialization.

## How It Works

### 1. Schema Source

Schemas are pulled from the `avro-schemas` Docker image during the build process:

```dockerfile
# Stage 1: Copy Avro schemas from avro-schemas image
FROM ghcr.io/rensights/avro-schemas:latest AS schemas

# Stage 2: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder
# Copy schemas from the schemas stage
COPY --from=schemas /schemas/schemas ./schemas/avro
```

### 2. Maven Build

The `pom.xml` is configured to generate Java classes from Avro schemas:

```xml
<plugin>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro-maven-plugin</artifactId>
    <configuration>
        <sourceDirectory>${project.basedir}/../schemas/avro</sourceDirectory>
        <outputDirectory>${project.build.directory}/generated-sources/avro</outputDirectory>
    </configuration>
</plugin>
```

### 3. Generated Classes

During `mvn compile`, the Avro Maven plugin:
1. Reads `.avsc` files from `schemas/avro/`
2. Generates Java classes in `target/generated-sources/avro/`
3. These classes are available at compile time

## Available Schemas

The following schemas are available from `avro-schemas`:

- `User.avsc` - User entity schema
- `Subscription.avsc` - Subscription entity schema
- `Device.avsc` - Device entity schema
- `AuthResponse.avsc` - Authentication response schema
- `DashboardStats.avsc` - Dashboard statistics schema

## Using Avro in Code

### Example: Converting JPA Entity to Avro

```java
import com.rensights.schema.User; // Generated class
import com.rensights.model.User; // JPA entity

public User toAvroUser(com.rensights.model.User user) {
    return User.newBuilder()
        .setId(user.getId().toString())
        .setEmail(user.getEmail())
        .setFirstName(user.getFirstName())
        .setLastName(user.getLastName())
        .build();
}
```

### Example: Serializing to Avro

```java
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.specific.SpecificDatumWriter;

User avroUser = toAvroUser(jpaUser);
ByteArrayOutputStream out = new ByteArrayOutputStream();
BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
SpecificDatumWriter<User> writer = new SpecificDatumWriter<>(User.class);
writer.write(avroUser, encoder);
encoder.flush();
byte[] avroBytes = out.toByteArray();
```

## Versioning

The backend uses the `latest` tag of `avro-schemas` by default, but supports version pinning via build argument.

### Current Release
- **v0.0.1** - First release (Nov 26, 2025)
  - Available at: `ghcr.io/rensights/avro-schemas:v0.0.1`
  - See: https://github.com/Rensights/avro-schemas/releases/tag/v0.0.1

### Using Versioned Tags

1. **Via Dockerfile build argument:**
   ```dockerfile
   # Pin to specific version
   ARG AVRO_SCHEMAS_VERSION=v0.0.1
   FROM ghcr.io/rensights/avro-schemas:${AVRO_SCHEMAS_VERSION} AS schemas
   ```

2. **Via Docker build:**
   ```bash
   docker build --build-arg AVRO_SCHEMAS_VERSION=v0.0.1 .
   ```

3. **Via CI/CD (GitHub Actions):**
   ```yaml
   - name: Build Docker image
     uses: docker/build-push-action@v5
     with:
       build-args: |
         AVRO_SCHEMAS_VERSION=v0.0.1
   ```

### Recommended Approach

- **Development:** Use `latest` for latest schemas
- **Production:** Pin to specific version (e.g., `v0.0.1`) for stability
- **Updates:** Test new versions in dev, then update production tag

## Updating Schemas

When schemas are updated in `avro-schemas`:

1. **Schema changes are pushed** to `avro-schemas` repository
2. **New image is built** and tagged (e.g., `v1.1.0`, `latest`)
3. **Backend rebuilds** will automatically pull the new schemas
4. **Maven regenerates** Java classes from new schemas

### Breaking Changes

If schema changes are breaking:
1. Update the backend code to match new schema structure
2. Test thoroughly
3. Consider using a version tag instead of `latest` during transition

## CI/CD Integration

The GitHub Actions workflow:
1. ✅ Authenticates to GHCR
2. ✅ Verifies `avro-schemas:latest` is available
3. ✅ Builds Docker image with schemas
4. ✅ Maven generates classes during build
5. ✅ Packages everything into final image

## Troubleshooting

### Schema Not Found

If you see errors about missing schemas:
- Check that `avro-schemas` image exists: `docker pull ghcr.io/rensights/avro-schemas:latest`
- Verify the image contains schemas: `docker run --rm ghcr.io/rensights/avro-schemas:latest ls -la /schemas/schemas`

### Generated Classes Missing

If Avro classes aren't generated:
- Run `mvn clean compile` to regenerate
- Check `target/generated-sources/avro/` directory
- Verify schemas are in `schemas/avro/` directory

### Build Fails with "Cannot find schema"

- Ensure Docker can pull `ghcr.io/rensights/avro-schemas:latest`
- Check GitHub Actions has `CONTAINER_TOKEN` secret configured
- Verify the avro-schemas repository has been built and pushed

## References

- [Avro Schemas Repository](https://github.com/Rensights/avro-schemas)
- [Avro Maven Plugin Documentation](https://avro.apache.org/docs/1.11.3/gettingstartedjava.html)
- [Avro Java API](https://avro.apache.org/docs/1.11.3/api/java/index.html)


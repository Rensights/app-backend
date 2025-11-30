# Release Notes

## Avro Schemas v0.0.1 Integration

**Release Date:** November 26, 2025  
**Avro Schemas Release:** [v0.0.1](https://github.com/Rensights/avro-schemas/releases/tag/v0.0.1)

### What's New

✅ **First Avro Schemas Release**
- Initial release of Avro schema definitions
- 5 schemas included: User, Subscription, Device, AuthResponse, DashboardStats
- Docker image available at: `ghcr.io/rensights/avro-schemas:v0.0.1`

### Integration Status

- ✅ Backend Dockerfile updated to use avro-schemas image
- ✅ Maven plugin configured to generate Java classes from schemas
- ✅ CI/CD workflow downloads schemas during test phase
- ✅ Multi-stage Docker build includes schemas

### Using v0.0.1

**Option 1: Pin to v0.0.1 (Recommended for Production)**

Update Dockerfile:
```dockerfile
ARG AVRO_SCHEMAS_VERSION=v0.0.1
FROM ghcr.io/rensights/avro-schemas:${AVRO_SCHEMAS_VERSION} AS schemas
```

**Option 2: Use Latest (Default)**

Current Dockerfile uses `latest` tag, which will automatically use the newest release.

### Available Schemas

1. **User.avsc** - User entity with email, name, tier, Stripe integration
2. **Subscription.avsc** - Subscription details with plan type and status
3. **Device.avsc** - Device information for authentication
4. **AuthResponse.avsc** - Authentication response structure
5. **DashboardStats.avsc** - Dashboard statistics data

### Next Steps

1. **Test the integration:**
   - Push to develop/main to trigger CI/CD
   - Verify schemas are downloaded and classes generated
   - Check that build completes successfully

2. **Use in code:**
   - Import generated classes: `com.rensights.schema.*`
   - See `AVRO_INTEGRATION.md` for usage examples

3. **For production:**
   - Consider pinning to `v0.0.1` for stability
   - Test schema changes in development first
   - Update version tag when new schemas are released

### References

- [Avro Schemas Repository](https://github.com/Rensights/avro-schemas)
- [v0.0.1 Release](https://github.com/Rensights/avro-schemas/releases/tag/v0.0.1)
- [Avro Integration Guide](./AVRO_INTEGRATION.md)








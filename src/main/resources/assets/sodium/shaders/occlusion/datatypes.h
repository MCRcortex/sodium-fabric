struct Range {
    uint start;
    uint count;
};

struct AABB {
    vec3 offset;
    vec3 size;
};

struct RegionMeta {
    uint id;
    uint sectionStart;
    uint sectionLength;
};

struct SectionMeta {
    uint id;
    uint regionId;

    //Offset to chunk section corner in world space
    vec3 sectionPos;

    //AABB in world space
    AABB bb;

    //TODO: try to make AABB relative to chunk corner and the chunk corner an int or something of chunk space coords
    // this should prevent any issues relating to loss of precision
};
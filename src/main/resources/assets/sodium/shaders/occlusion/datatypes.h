struct Range {
    uint start;
    uint count;
};

struct AABB {
    vec4 offset;
    vec4 size;
};

struct RegionMeta {
    uint id;

    uint sectionStart;
    uint sectionCount;

    uint ALIGNMENT_PADDING;

    //Bounding box of the region and all sections inside
    AABB bb;
};

struct SectionMeta {
    uint id;
    uint regionId;

    uint ALIGNMENT_PADDING1;
    uint ALIGNMENT_PADDING2;

    //Offset to chunk section corner in world space
    vec4 sectionPos;

    //AABB in world space
    AABB bb;

    //TODO: try to make AABB relative to chunk corner and the chunk corner an int or something of chunk space coords
    // this should prevent any issues relating to loss of precision
};

struct DrawElementsIndirectCommand {
    uint  count;
    uint  instanceCount;
    uint  firstIndex;
    uint  baseVertex;
    uint  baseInstance;
};

struct DispatchIndirectCommand {
    uint  num_groups_x;
    uint  num_groups_y;
    uint  num_groups_z;
};
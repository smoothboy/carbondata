package org.carbondata.core.cache.dictionary;

import java.io.IOException;

import org.carbondata.common.logging.LogService;
import org.carbondata.common.logging.LogServiceFactory;
import org.carbondata.core.cache.Cache;
import org.carbondata.core.cache.CacheType;
import org.carbondata.core.cache.CarbonLRUCache;
import org.carbondata.core.constants.CarbonCommonConstants;
import org.carbondata.core.datastorage.store.filesystem.CarbonFile;
import org.carbondata.core.datastorage.store.impl.FileFactory;
import org.carbondata.core.reader.CarbonDictionaryColumnMetaChunk;
import org.carbondata.core.reader.CarbonDictionaryMetadataReaderImpl;
import org.carbondata.core.util.CarbonCoreLogEvent;
import org.carbondata.core.util.CarbonDictionaryUtil;
import org.carbondata.core.util.CarbonUtil;

/**
 * Abstract class which implements methods common to reverse and forward dictionary cache
 */
public abstract class AbstractDictionaryCache<K extends DictionaryColumnUniqueIdentifier,
        V extends Dictionary>
        implements Cache<DictionaryColumnUniqueIdentifier, Dictionary> {

    /**
     * Attribute for Carbon LOGGER
     */
    private static final LogService LOGGER =
            LogServiceFactory.getLogService(AbstractDictionaryCache.class.getName());
    /**
     * thread pool size to be used for dictionary data reading
     */
    protected static final int FIXED_THREAD_POOL_SIZE = 5;

    /**
     * LRU cache variable
     */
    protected CarbonLRUCache carbonLRUCache;

    /**
     * c store path
     */
    protected String carbonStorePath;

    /**
     * @param carbonStorePath
     * @param carbonLRUCache
     */
    public AbstractDictionaryCache(String carbonStorePath, CarbonLRUCache carbonLRUCache) {
        this.carbonStorePath = carbonStorePath;
        this.carbonLRUCache = carbonLRUCache;
    }

    /**
     * This method will check if dictionary and its metadata file exists for a given column
     *
     * @param dictionaryColumnUniqueIdentifier unique identifier which contains dbName,
     *                                         tableName and columnIdentifier
     * @return
     */
    protected boolean isFileExistsForGivenColumn(
            DictionaryColumnUniqueIdentifier dictionaryColumnUniqueIdentifier) {
        String dictionaryLocation = CarbonDictionaryUtil
                .getDirectoryPath(dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(),
                        carbonStorePath, false);
        String dictionaryFilePath = CarbonDictionaryUtil
                .getDictionaryFilePath(dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(),
                        dictionaryLocation, dictionaryColumnUniqueIdentifier.getColumnIdentifier(),
                        false);
        String dictionaryMetadataFilePath = CarbonDictionaryUtil.getDictionaryMetadataFilePath(
                dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(), dictionaryLocation,
                dictionaryColumnUniqueIdentifier.getColumnIdentifier(), false);
        // check if both dictionary and its metadata file exists for a given column
        return CarbonUtil.isFileExists(dictionaryFilePath) && CarbonUtil
                .isFileExists(dictionaryMetadataFilePath);
    }

    /**
     * This method will read dictionary metadata file and return the dictionary meta chunks
     *
     * @param dictionaryColumnUniqueIdentifier
     * @return list of dictionary metadata chunks
     * @throws IOException read and close method throws IO exception
     */
    protected CarbonDictionaryColumnMetaChunk readLastChunkFromDictionaryMetadataFile(
            DictionaryColumnUniqueIdentifier dictionaryColumnUniqueIdentifier) throws IOException {
        CarbonDictionaryMetadataReaderImpl columnMetadataReaderImpl =
                new CarbonDictionaryMetadataReaderImpl(this.carbonStorePath,
                        dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(),
                        dictionaryColumnUniqueIdentifier.getColumnIdentifier(), false);
        CarbonDictionaryColumnMetaChunk carbonDictionaryColumnMetaChunk = null;
        // read metadata file
        try {
            carbonDictionaryColumnMetaChunk =
                    columnMetadataReaderImpl.readLastEntryOfDictionaryMetaChunk();
        } finally {
            // close the metadata reader
            columnMetadataReaderImpl.close();
        }
        return carbonDictionaryColumnMetaChunk;
    }

    /**
     * This method will return the last modified time for a given file
     *
     * @param dictionaryColumnUniqueIdentifier
     * @return
     */
    protected long getDictionaryFileLastModifiedTime(
            DictionaryColumnUniqueIdentifier dictionaryColumnUniqueIdentifier) {
        String dictionaryLocation = CarbonDictionaryUtil
                .getDirectoryPath(dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(),
                        carbonStorePath, false);
        String dictionaryFilePath = CarbonDictionaryUtil
                .getDictionaryFilePath(dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(),
                        dictionaryLocation, dictionaryColumnUniqueIdentifier.getColumnIdentifier(),
                        false);
        FileFactory.FileType fileType = FileFactory.getFileType(dictionaryFilePath);
        CarbonFile carbonFile = FileFactory.getCarbonFile(dictionaryFilePath, fileType);
        return carbonFile.getLastModifiedTime();
    }

    /**
     * This method will get the value for the given key. If value does not exist
     * for the given key, it will check and load the value.
     *
     * @param dictionaryColumnUniqueIdentifier unique identifier which contains dbName,
     *                                         tableName and columnIdentifier
     * @param dictionaryInfo
     * @param lruCacheKey
     * @return true if key added to lru cache and dictionary data loaded successfully,
     * false otherwise
     */
    protected boolean checkAndLoadDictionaryData(
            DictionaryColumnUniqueIdentifier dictionaryColumnUniqueIdentifier,
            DictionaryInfo dictionaryInfo, String lruCacheKey) {
        boolean dictionaryLoadSuccessfull = false;
        try {
            // read last segment dictionary meta chunk entry to get the end offset of file
            CarbonDictionaryColumnMetaChunk carbonDictionaryColumnMetaChunk =
                    readLastChunkFromDictionaryMetadataFile(dictionaryColumnUniqueIdentifier);
            // required size will be size total size of file - offset till file is
            // already read
            long requiredSize = carbonDictionaryColumnMetaChunk.getEnd_offset() - dictionaryInfo
                    .getMemorySize();
            long lastModifiedTime =
                    getDictionaryFileLastModifiedTime(dictionaryColumnUniqueIdentifier);
            // if current file stamp and end offset greater than timestamp amd end offset
            // stored in dictionary info then only
            // read data from dictionary file
            if (requiredSize > 0 && lastModifiedTime > dictionaryInfo.getFileTimeStamp()) {
                synchronized (dictionaryInfo) {
                    requiredSize = carbonDictionaryColumnMetaChunk.getEnd_offset() - dictionaryInfo
                            .getMemorySize();
                    lastModifiedTime =
                            getDictionaryFileLastModifiedTime(dictionaryColumnUniqueIdentifier);
                    // Double Check :
                    // if current file stamp and end offset greater than timestamp amd end offset
                    // stored in dictionary info then only
                    // read data from dictionary file
                    if (requiredSize > 0 && lastModifiedTime > dictionaryInfo.getFileTimeStamp()) {
                        boolean columnAddedToLRUCache =
                                carbonLRUCache.put(lruCacheKey, dictionaryInfo, requiredSize);
                        // if column is successfully added to lru cache then only load the
                        // dictionary data
                        if (columnAddedToLRUCache) {
                            // load dictionary data
                            loadDictionaryData(dictionaryInfo, dictionaryColumnUniqueIdentifier,
                                    dictionaryInfo.getMemorySize(),
                                    carbonDictionaryColumnMetaChunk.getEnd_offset());
                            // increment the column access count
                            incrementDictionaryAccessCount(dictionaryInfo);
                            // set ne file timestamp
                            dictionaryInfo.setFileTimeStamp(lastModifiedTime);
                            // set the end offset till where file is read
                            dictionaryInfo.setOffsetTillFileIsRead(
                                    carbonDictionaryColumnMetaChunk.getEnd_offset());
                            dictionaryLoadSuccessfull = true;
                        }
                    } else {
                        // if timestamp and end offset is unchanged then dictionary
                        // load should be marked success
                        dictionaryLoadSuccessfull = true;
                    }
                }
            } else {
                // if timestamp and end offset is unchanged then dictionary
                // load should be marked success
                dictionaryLoadSuccessfull = true;
            }
        } catch (IOException e) {
            LOGGER.error(CarbonCoreLogEvent.UNIBI_CARBONCORE_MSG,
                    "Error loading the dictionary data: " + e.getMessage());
        }
        return dictionaryLoadSuccessfull;
    }

    /**
     * This method will prepare the lru cache key and return the same
     *
     * @param columnIdentifier
     * @return
     */
    protected String getLruCacheKey(String columnIdentifier, CacheType cacheType) {
        String lruCacheKey =
                columnIdentifier + CarbonCommonConstants.UNDERSCORE + cacheType.getCacheName();
        return lruCacheKey;
    }

    /**
     * This method will check and load the dictionary file in memory for a given column
     *
     * @param dictionaryInfo                   holds dictionary information and data
     * @param dictionaryColumnUniqueIdentifier unique identifier which contains dbName,
     *                                         tableName and columnIdentifier
     * @param dictionaryChunkStartOffset       start offset from where dictionary file has to
     *                                         be read
     * @param dictionaryChunkEndOffset         end offset till where dictionary file has to
     *                                         be read
     * @throws IOException
     */
    protected void loadDictionaryData(DictionaryInfo dictionaryInfo,
            DictionaryColumnUniqueIdentifier dictionaryColumnUniqueIdentifier,
            long dictionaryChunkStartOffset, long dictionaryChunkEndOffset) throws IOException {
        DictionaryCacheLoader dictionaryCacheLoader = new DictionaryCacheLoaderImpl(
                dictionaryColumnUniqueIdentifier.getCarbonTableIdentifier(), carbonStorePath);
        dictionaryCacheLoader
                .load(dictionaryInfo, dictionaryColumnUniqueIdentifier.getColumnIdentifier(),
                        dictionaryChunkStartOffset, dictionaryChunkEndOffset);
    }

    /**
     * This method will increment the access count for a given dictionary column
     *
     * @param dictionaryInfo
     */
    protected void incrementDictionaryAccessCount(DictionaryInfo dictionaryInfo) {
        dictionaryInfo.incrementAccessCount();
    }
}
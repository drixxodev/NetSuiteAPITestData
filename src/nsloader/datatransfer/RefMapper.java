package nsloader.datatransfer;

import nsloader.wsproxy.platform.core.BaseRef;
import nsloader.wsproxy.platform.core.CustomRecordRef;
import nsloader.wsproxy.platform.core.RecordRef;
import nsloader.wsproxy.platform.core.types.RecordType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Description: Creates a BaseRef child object - only working with ID fields designated for Delete operation
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class RefMapper
{
	public static final Set<String> restrictedHeaders = new HashSet<String>();

	public static final String INTERNAL_ID = "internalId";
	public static final String EXTERNAL_ID = "externalId";
	public static final String TYPE_ID = "typeId";

	static
	{
		restrictedHeaders.add(INTERNAL_ID);
		restrictedHeaders.add(EXTERNAL_ID);
		restrictedHeaders.add(TYPE_ID);
	}

	private final Map<String, Integer> headersWithIndices = new HashMap<String, Integer>();
	private final boolean isCustomRecord;
	private final String recordType;

	public RefMapper(String recordType, String[] headers)
	{
		isCustomRecord = "CustomRecord".equals(recordType);
		this.recordType = recordType;

		for (int i = 0; i < headers.length; i++)
		{
			if (restrictedHeaders.contains(headers[i]))
			{
				headersWithIndices.put(headers[i], i);
			}
		}

		if (headersWithIndices.get(INTERNAL_ID) == null && headersWithIndices.get(EXTERNAL_ID) == null)
		{
			throw new RuntimeException(String.format("Please specify either %s or %s", INTERNAL_ID, EXTERNAL_ID));
		}

		if (isCustomRecord && headersWithIndices.get(TYPE_ID) == null)
		{
			throw new RuntimeException(String.format("Please specify %s", TYPE_ID));
		}
	}

	public BaseRef map(String[] data) throws Exception
	{
		BaseRef toRet = isCustomRecord ? new CustomRecordRef() : new RecordRef();
		for (Map.Entry<String, Integer> entry : headersWithIndices.entrySet())
		{
			String value = data[entry.getValue()];
			if (value != null && value.trim().length() > 0)
			{
				Method setter = RecordMapper.findMethodByNameAndParamCount(toRet.getClass(), "set" + RecordMapper.toInitCaps(entry.getKey()), 1);
				setter.invoke(toRet, value);
			}
		}

		if (!isCustomRecord)
		{
			((RecordRef)toRet).setType(RecordType.fromString(RecordMapper.toInitLower(recordType)));
		}

		return toRet;
	}
}

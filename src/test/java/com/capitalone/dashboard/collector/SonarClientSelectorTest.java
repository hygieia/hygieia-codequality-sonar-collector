package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.client.RestOperationsSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.Matchers.instanceOf;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(MockitoExtension.class)
public class SonarClientSelectorTest {

    @InjectMocks
    private SonarClientSelector selector;
    @Mock
    private DefaultSonarClient defaultSonarClient;
    @Mock
    private DefaultSonar6Client defaultSonar6Client;
    @Mock
    private DefaultSonar56Client defaultSonar56Client;
    @Mock
    private DefaultSonar8Client defaultSonar8Client;
    @Mock
    private RestOperationsSupplier restOperationsSupplier;

    @Test
    public void getSonarClient4() throws Exception {
        SonarClient sonarClient = selector.getSonarClient((double) 4);
        assertThat(sonarClient).isInstanceOf(DefaultSonarClient.class);
    }

    @Test
    public void getSonarClient56() throws Exception {
        SonarClient sonarClient = selector.getSonarClient((double) 5.6);
        assertThat(sonarClient).isInstanceOf(DefaultSonar56Client.class);
    }

    @Test
    public void getSonarClientNull() throws Exception {
        SonarClient sonarClient = selector.getSonarClient(null);
        assertThat(sonarClient).isInstanceOf(DefaultSonarClient.class);
    }

    @Test
    public void getSonarClient54() throws Exception {
        SonarClient sonarClient = selector.getSonarClient(5.4);
        assertThat(sonarClient).isInstanceOf(DefaultSonarClient.class);
    }

    @Test
    public void getSonarClient6() throws Exception {
        SonarClient sonarClient = selector.getSonarClient(6.31);
        assertThat(sonarClient).isInstanceOf(DefaultSonar6Client.class);
    }

    @Test
    public void getSonarClient83() throws Exception {
        SonarClient sonarClient = selector.getSonarClient(8.3);
        assertThat(sonarClient).isInstanceOf(DefaultSonar8Client.class);
    }

}
